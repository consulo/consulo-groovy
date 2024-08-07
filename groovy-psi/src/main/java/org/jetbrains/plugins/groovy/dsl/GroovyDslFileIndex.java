/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.java.language.psi.PsiAnnotation;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.component.ProcessCanceledException;
import consulo.component.util.ModificationTracker;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.DefaultFileTypeSpecificInputFilter;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileContent;
import consulo.language.psi.stub.ScalarIndexExtension;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.concurrent.ConcurrencyUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.io.URLUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.VirtualFileAdapter;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;
import org.jetbrains.plugins.groovy.dsl.DslActivationStatus.Status;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyDslFileIndex extends ScalarIndexExtension<String> {

  private static final Key<Pair<GroovyDslExecutor, Long>> CACHED_EXECUTOR = Key.create("CachedGdslExecutor");
  private static final Key<CachedValue<List<GroovyDslScript>>> SCRIPTS_CACHE = Key.create("GdslScriptCache");
  private static final Logger LOG = Logger.getInstance(GroovyDslFileIndex.class);
  private static final String OUR_KEY = "ourKey";
  public static final ID<String, Void> NAME = ID.create("GroovyDslFileIndex");

  private static final MultiMap<String, LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>> filesInProcessing = MultiMap.createConcurrent();

  private static final ThreadPoolExecutor ourPool = new ThreadPoolExecutor(4,
                                                                           4,
                                                                           1,
                                                                           TimeUnit.SECONDS,
                                                                           new LinkedBlockingQueue<Runnable>(),
                                                                           ConcurrencyUtil.newNamedThreadFactory("Groovy DSL File Index " +
                                                                                                                   "Executor"));

  static {
    ourPool.allowCoreThreadTimeOut(true);
  }

  private final MyDataIndexer myDataIndexer = new MyDataIndexer();
  private final EnumeratorStringDescriptor myKeyDescriptor = new EnumeratorStringDescriptor();

  public GroovyDslFileIndex() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void contentsChanged(@Nonnull VirtualFileEvent event) {
        final VirtualFile file = event.getFile();
        if (event.isFromRefresh() || !GdslUtil.GDSL_FILTER.value(file) || getStatus(file) != Status.ACTIVE) {
          return;
        }
        disableFile(file, Status.MODIFIED, null);
      }
    });
  }

  @Override
  @Nonnull
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  @Nonnull
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myDataIndexer;
  }

  @Nonnull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @Nonnull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new MyInputFilter();
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public int getVersion() {
    return 0;
  }

  @Nullable
  public static String getError(VirtualFile file) {
    DslActivationStatus.Entry info = DslActivationStatus.getInstance().getGdslFileInfo(file);
    return info == null ? null : info.error;
  }

  public static boolean isActivated(@Nonnull VirtualFile file) {
    return getStatus(file) == Status.ACTIVE;
  }

  public static void activate(final VirtualFile vfile) {
    setStatusAndError(vfile, Status.ACTIVE, null);
    clearScriptCache();
  }

  public static Status getStatus(@Nonnull VirtualFile file) {
    DslActivationStatus.Entry info = DslActivationStatus.getInstance().getGdslFileInfo(file);
    return info == null ? Status.ACTIVE : info.status;
  }

  private static void clearScriptCache() {
    Application app = ApplicationManager.getApplication();
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          project.putUserData(SCRIPTS_CACHE, null);
          PsiModificationTracker.getInstance(project).incCounter();
        }
      }
    }, app.getDisposed());
  }

  public static void disableFile(@Nonnull VirtualFile vfile, @Nonnull Status status, @Nullable String error) {
    assert status != Status.ACTIVE;
    setStatusAndError(vfile, status, error);
    vfile.putUserData(CACHED_EXECUTOR, null);
    clearScriptCache();
  }

  private static void setStatusAndError(@Nonnull VirtualFile vfile, @Nonnull Status status, @Nullable String error) {
    DslActivationStatus.Entry entry = DslActivationStatus.getInstance().getGdslFileInfoOrCreate(vfile);
    entry.status = status;
    entry.error = error;
  }

  @Nullable
  private static GroovyDslExecutor getCachedExecutor(@Nonnull final VirtualFile file, final long stamp) {
    final Pair<GroovyDslExecutor, Long> pair = file.getUserData(CACHED_EXECUTOR);
    if (pair == null || pair.second.longValue() != stamp) {
      return null;
    }
    return pair.first;
  }

  @Nullable
  public static PsiClassType processScriptSuperClasses(@Nonnull GroovyFile scriptFile) {
    if (!scriptFile.isScript()) {
      return null;
    }

    final VirtualFile virtualFile = scriptFile.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }
    final String filePath = virtualFile.getPath();


    List<Trinity<String, String, GroovyDslScript>> supers = ContainerUtil.newArrayList();
    final Project project = scriptFile.getProject();
    for (GroovyDslScript script : getDslScripts(project)) {
      final MultiMap staticInfo = script.getStaticInfo();
      //noinspection unchecked
      final Collection infos = staticInfo != null ? staticInfo.get("scriptSuperClass") : Collections.emptyList();

      for (Object info : infos) {
        if (info instanceof Map) {
          final Map map = (Map)info;

          final Object _pattern = map.get("pattern");
          final Object _superClass = map.get("superClass");

          if (_pattern instanceof String && _superClass instanceof String) {
            final String pattern = (String)_pattern;
            final String superClass = (String)_superClass;

            try {
              if (Pattern.matches(".*" + pattern, filePath)) {
                supers.add(Trinity.create(superClass, pattern, script));
              }
            }
            catch (RuntimeException e) {
              script.handleDslError(e);
            }
          }
        }
      }
    }

    if (!supers.isEmpty()) {
      final String className = supers.get(0).first;
      final GroovyDslScript script = supers.get(0).third;
      try {
        return TypesUtil.createTypeByFQClassName(className, scriptFile);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (RuntimeException e) {
        script.handleDslError(e);
        return null;
      }
    }
    /*else if (supers.size() > 1) {
      StringBuilder buffer = new StringBuilder("Several script super class patterns match file ").append(filePath)
      .append(". <p> ");
      for (Trinity<String, String, GroovyDslScript> aSuper : supers) {
        buffer.append(aSuper.third.getFilePath()).append(" ").append(aSuper.second).append('\n');
      }
      NOTIFICATION_GROUP.createNotification("DSL script execution error", buffer.toString(), NotificationType.ERROR,
      null).notify(project);
      return null;
    }*/
    else {
      return null;
    }
  }

  public static boolean processExecutors(PsiType psiType,
                                         PsiElement place,
                                         final PsiScopeProcessor processor,
                                         ResolveState state) {
    if (insideAnnotation(place)) {
      // Basic filter, all DSL contexts are applicable for reference expressions only
      return true;
    }

    final String qname = psiType.getCanonicalText();

    final PsiFile placeFile = place.getContainingFile().getOriginalFile();

    final DelegatingScopeProcessor nameChecker = new DelegatingScopeProcessor(processor) {
      @Override
      public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
        if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
          return processor.execute(element, state);
        }
        else if (element instanceof PsiNamedElement) {
          return ResolveUtil.processElement(processor, (PsiNamedElement)element, state);
        }
        else {
          return processor.execute(element, state);
        }
      }
    };

    for (GroovyDslScript script : getDslScripts(place.getProject())) {
      if (!script.processExecutor(nameChecker, psiType, place, placeFile, qname, state)) {
        return false;
      }
    }

    return true;
  }

  private static boolean insideAnnotation(@Nullable PsiElement place) {
    while (place != null) {
      if (place instanceof PsiAnnotation) {
        return true;
      }
      if (place instanceof GrClosableBlock ||
        place instanceof GrTypeDefinition ||
        place instanceof PsiFile) {
        return false;
      }
      place = place.getParent();
    }
    return false;
  }

  private static List<VirtualFile> getGdslFiles(final Project project) {
    final List<VirtualFile> result = ContainerUtil.newArrayList();
    result.addAll(getBundledGdslFiles());
    result.addAll(getProjectGdslFiles(project));
    return result;
  }

  private static List<VirtualFile> getBundledGdslFiles() {
    final List<VirtualFile> result = ContainerUtil.newArrayList();
    for (File file : getBundledScriptFolders()) {
      if (file.exists()) {
        File[] children = file.listFiles();
        if (children != null) {
          for (File child : children) {
            final String fileName = child.getName();
            if (fileName.endsWith(".gdsl")) {
              String path = FileUtil.toSystemIndependentName(child.getPath());
              String url = VirtualFileManager.constructUrl(URLUtil.FILE_PROTOCOL, path);
              ContainerUtil.addIfNotNull(result, VirtualFileManager.getInstance()
                                                                   .refreshAndFindFileByUrl(url));
            }
          }
        }
      }
    }
    return result;
  }

  private static List<VirtualFile> getProjectGdslFiles(Project project) {
    final List<VirtualFile> result = ContainerUtil.newArrayList();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final GlobalSearchScope scope = GlobalSearchScope.allScope(project);

    for (VirtualFile vfile : FileBasedIndex.getInstance().getContainingFiles(NAME, OUR_KEY, scope)) {
      if (!vfile.isValid()) {
        continue;
      }
      if (!GdslUtil.GDSL_FILTER.value(vfile)) {
        LOG.error("Index returned non-gdsl file: " + vfile);
        continue;
      }
      if (fileIndex.isInLibrarySource(vfile)) {
        continue;
      }
      if (!fileIndex.isInLibraryClasses(vfile)) {
        if (!fileIndex.isInSourceContent(vfile) || !isActivated(vfile)) {
          continue;
        }
      }

      result.add(vfile);
    }
    return result;
  }


  @Nonnull
  private static Set<File> getBundledScriptFolders() {
    final GroovyFrameworkConfigNotification[] extensions = GroovyFrameworkConfigNotification.EP_NAME
      .getExtensions();
    Set<Class> classes = new HashSet<Class>(ContainerUtil.map2Set(extensions, notification -> notification.getClass()));
    classes.add(GroovyFrameworkConfigNotification.class); // for default extension

    // perhaps a separate extension for that?
    Set<File> scriptFolders = new LinkedHashSet<File>();
    for (Class aClass : classes) {
      File jarPath = new File(ClassPathUtil.getJarPathForClass(aClass));
      if (jarPath.isFile()) {
        jarPath = jarPath.getParentFile();
      }
      scriptFolders.add(new File(jarPath, "standardDsls"));
    }
    return scriptFolders;
  }

  private static List<GroovyDslScript> getDslScripts(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, SCRIPTS_CACHE, new CachedValueProvider<List<GroovyDslScript>>() {
      @Override
      public Result<List<GroovyDslScript>> compute() {
        if (GdslUtil.ourGdslStopped) {
          return Result.create(Collections.<GroovyDslScript>emptyList(),
                               ModificationTracker.NEVER_CHANGED);
        }

        // eagerly initialize some services used by background gdsl parsing threads
        // because service init requires a read action
        // and there could be a deadlock with a write action waiting already on EDT
        // if current thread is inside a non-cancellable read action
        DslActivationStatus.getInstance();

        int count = 0;

        List<GroovyDslScript> result = new ArrayList<GroovyDslScript>();

        final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>
          queue = new
          LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>>();

        for (VirtualFile vfile : getGdslFiles(project)) {
          final long stamp = vfile.getModificationStamp();
          final GroovyDslExecutor cached = getCachedExecutor(vfile, stamp);
          if (cached == null) {
            scheduleParsing(queue,
                            project,
                            vfile,
                            stamp,
                            vfile.loadText().toString());
            count++;
          }
          else {
            result.add(new GroovyDslScript(project,
                                           vfile,
                                           cached,
                                           vfile.getPath()));
          }
        }

        try {
          while (count > 0 && !GdslUtil.ourGdslStopped) {
            ProgressManager.checkCanceled();
            final Pair<VirtualFile, GroovyDslExecutor> pair =
              queue.poll(20, TimeUnit.MILLISECONDS);
            if (pair != null) {
              count--;
              if (pair.second != null) {
                result.add(new GroovyDslScript(project,
                                               pair.first,
                                               pair.second,
                                               pair.first.getPath()));
              }
            }
          }
        }
        catch (InterruptedException e) {
          LOG.error(e);
        }

        return Result.create(result,
                             PsiModificationTracker.MODIFICATION_COUNT,
                             ProjectRootManager.getInstance(project));
      }
    }, false);
  }

  private static class MyDataIndexer implements DataIndexer<String, Void, FileContent> {

    @Override
    @Nonnull
    public Map<String, Void> map(@Nonnull final FileContent inputData) {
      return Collections.singletonMap(OUR_KEY, null);
    }
  }

  private static class MyInputFilter extends DefaultFileTypeSpecificInputFilter {
    MyInputFilter() {
      super(GroovyFileType.GROOVY_FILE_TYPE);
    }

    @Override
    public boolean acceptInput(@Nullable Project project, @Nonnull final VirtualFile file) {
      return GdslUtil.GDSL_FILTER.value(file);
    }
  }

  private static void scheduleParsing(final LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue,
                                      final Project project,
                                      final VirtualFile vfile,
                                      final long stamp,
                                      final String text) {
    final String fileUrl = vfile.getUrl();

    final Runnable parseScript = new Runnable() {
      @Override
      public void run() {
        GroovyDslExecutor executor = getCachedExecutor(vfile, stamp);
        try {
          if (executor == null && isActivated(vfile)) {
            executor = createExecutor(text, vfile, project);
            // executor is not only time-consuming to create, but also takes some PermGenSpace
            // => we can't afford garbage-collecting it together with PsiFile
            // => cache globally by file instance
            vfile.putUserData(CACHED_EXECUTOR, Pair.create(executor, stamp));
            if (executor != null) {
              activate(vfile);
            }
          }
        }
        finally {
          // access to our MultiMap should be synchronized
          synchronized (filesInProcessing) {
            // put evaluated executor to all queues
            for (LinkedBlockingQueue<Pair<VirtualFile, GroovyDslExecutor>> queue : filesInProcessing.remove
              (fileUrl)) {
              queue.offer(Pair.create(vfile, executor));
            }
          }
        }
      }
    };

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (filesInProcessing) { //ensure that only one thread calculates dsl executor
      final boolean isNewRequest = !filesInProcessing.containsKey(fileUrl);
      filesInProcessing.putValue(fileUrl, queue);
      if (isNewRequest) {
        ourPool.execute(parseScript);
      }
    }
  }

  @Nullable
  private static GroovyDslExecutor createExecutor(String text, VirtualFile vfile, final Project project) {
    if (GdslUtil.ourGdslStopped) {
      return null;
    }

    try {
      return new GroovyDslExecutor(text, vfile.getName());
    }
    catch (final Throwable e) {
      if (project.isDisposed()) {
        LOG.error(e);
        return null;
      }

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error(e);
        return null;
      }
      DslErrorReporter.getInstance().invokeDslErrorPopup(e, project, vfile);

      //noinspection InstanceofCatchParameter
      if (e instanceof OutOfMemoryError) {
        GdslUtil.stopGdsl();
        throw (Error)e;
      }
      //noinspection InstanceofCatchParameter
      if (e instanceof NoClassDefFoundError) {
        GdslUtil.stopGdsl();
        throw (NoClassDefFoundError)e;
      }

      return null;
    }
  }
}
