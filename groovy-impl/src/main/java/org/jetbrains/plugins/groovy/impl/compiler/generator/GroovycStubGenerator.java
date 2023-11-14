/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.compiler.generator;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessRule;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressIndicator;
import consulo.compiler.CompileContext;
import consulo.compiler.CompileContextEx;
import consulo.compiler.CompilerPaths;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.scope.FileSetCompileScope;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.compiler.util.CompilerUtil;
import consulo.content.ContentIterator;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.project.Project;
import consulo.util.collection.Chunk;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.FactoryMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.compiler.GroovyCompilerBase;
import org.jetbrains.plugins.groovy.impl.compiler.GroovyCompilerConfiguration;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author peter
 */
@ExtensionImpl(id = "groovy-stub-generator")
public class GroovycStubGenerator extends GroovyCompilerBase {
  private static Logger LOG = Logger.getInstance(GroovycStubGenerator.class);

  public static final String GROOVY_STUBS = "groovyStubs";

  @Inject
  public GroovycStubGenerator(Project project) {
    super(project);
  }

  @Override
  public void compile(CompileContext compileContext, Chunk<Module> moduleChunk, VirtualFile[] virtualFiles, OutputSink sink) {
    final ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);

    Map<Pair<Module, Boolean>, Boolean> hasJava = FactoryMap.create(key -> containsJavaSources(key.first, key.second));

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();

    List<VirtualFile> total = new ArrayList<>();
    for (final VirtualFile virtualFile : virtualFiles) {
      if (!excluded.isExcluded(virtualFile) && GroovyNamesUtil.isIdentifier(virtualFile.getNameWithoutExtension())) {
        Module module = index.getModuleForFile(virtualFile);
        if (module == null || hasJava.get(Pair.create(module, index.isInTestSourceContent(virtualFile)))) {
          total.add(virtualFile);
        }
      }
    }

    if (total.isEmpty()) {
      return;
    }

    //long l = System.currentTimeMillis();
    super.compile(compileContext, moduleChunk, consulo.ide.impl.idea.openapi.vfs.VfsUtil.toVirtualFileArray(total), sink);
    //System.out.println("Stub generation took " + (System.currentTimeMillis() - l));
  }

  @Nonnull
  @Override
  public FileType[] getInputFileTypes() {
    return new FileType[]{
      JavaFileType.INSTANCE,
      GroovyFileType.GROOVY_FILE_TYPE
    };
  }

  @Nonnull
  @Override
  public FileType[] getOutputFileTypes() {
    return new FileType[]{JavaFileType.INSTANCE};
  }

  private static boolean containsJavaSources(Module module, boolean inTests) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    for (ContentEntry entry : rootManager.getContentEntries()) {
      for (ContentFolder folder : entry.getFolders(LanguageContentFolderScopes.all(false))) {
        VirtualFile dir = folder.getFile();
        if ((!inTests && folder.getType() == ProductionContentFolderTypeProvider.getInstance() || folder.getType() ==
          TestContentFolderTypeProvider.getInstance() && inTests) && dir != null) {
          if (!rootManager.getFileIndex().iterateContentUnderDirectory(dir, new ContentIterator() {
            @Override
            public boolean processFile(VirtualFile fileOrDir) {
              if (!fileOrDir.isDirectory() && JavaFileType.INSTANCE == fileOrDir.getFileType()) {
                return false;
              }
              return true;
            }
          })) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  protected void compileFiles(CompileContext compileContext,
                              Module module,
                              final List<VirtualFile> toCompile,
                              OutputSink sink,
                              boolean tests) {
    final File outDir = getStubOutput(module, tests);
    outDir.mkdirs();

    final VirtualFile tempOutput = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outDir);
    assert tempOutput != null;
    cleanDirectory(tempOutput);

    ((CompileContextEx)compileContext).assignModule(tempOutput, module, tests, this);

    ProgressIndicator indicator = compileContext.getProgressIndicator();
    indicator.pushState();

    try {
      final GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, new HashSet<>(toCompile));
      for (int i = 0; i < toCompile.size(); i++) {
        indicator.setFraction((double)i / toCompile.size());

        final Collection<VirtualFile> stubFiles = generateItems(generator, toCompile.get(i), tempOutput, compileContext, myProject);
        ((CompileContextEx)compileContext).addScope(new FileSetCompileScope(stubFiles, new Module[]{module}));
      }
    }
    finally {
      indicator.popState();
    }
  }

  private static File getStubOutput(Module module, boolean tests) {
    final Project project = module.getProject();
    final String rootPath = CompilerPaths.getGeneratedDataDirectory(project).getPath() + "/" + GROOVY_STUBS + "/";
    return new File(rootPath + module.getName() + "/" + (tests ? "tests" : "production") + "/");
  }

  @Nullable
  public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
    final String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
    final int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
    if (stubs < 0 || stubs >= components.length - 3) {
      return null;
    }

    final String moduleName = components[stubs + 1];
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module == null) {
      return null;
    }

    final String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 3, components.length), ".");
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
  }

  private void cleanDirectory(final VirtualFile dir) {
    WriteAction.runAndWait(() -> {
      VirtualFileUtil.processFilesRecursively(dir, new Predicate<>() {
        @Override
        public boolean test(VirtualFile virtualFile) {
          if (!virtualFile.isDirectory()) {
            try {
              virtualFile.delete(this);
            }
            catch (IOException e) {
              LOG.info(e);
            }
          }
          return true;
        }
      });
    });
  }

  @Nonnull
  public String getDescription() {
    return "Groovy to java source code generator";
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public static Collection<VirtualFile> generateItems(final GroovyToJavaGenerator generator,
                                                      final VirtualFile item,
                                                      final VirtualFile outputRootDirectory,
                                                      CompileContext context,
                                                      final Project project) {
    ProgressIndicator indicator = context.getProgressIndicator();
    indicator.setText("Generating stubs for " + item.getName() + "...");

    if (LOG.isDebugEnabled()) {
      LOG.debug("Generating stubs for " + item.getName() + "...");
    }

    final Map<String, CharSequence> output;

    output = AccessRule.read(() -> generator.generateStubs((GroovyFile)PsiManager.getInstance(project).findFile(item)));

    return writeStubs(outputRootDirectory, output, item);
  }

  private static List<VirtualFile> writeStubs(VirtualFile outputRootDirectory, Map<String, CharSequence> output, VirtualFile src) {
    final ArrayList<VirtualFile> stubs = ContainerUtil.newArrayList();
    for (String relativePath : output.keySet()) {
      final File stubFile = new File(outputRootDirectory.getPath(), relativePath);
      FileUtil.createIfDoesntExist(stubFile);
      try {
        FileUtil.writeToFile(stubFile, output.get(relativePath).toString().getBytes(src.getCharset()));
      }
      catch (IOException e) {
        LOG.error(e);
      }
      CompilerUtil.refreshIOFile(stubFile);
      ContainerUtil.addIfNotNull(stubs, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(stubFile));
    }
    return stubs;
  }
}
