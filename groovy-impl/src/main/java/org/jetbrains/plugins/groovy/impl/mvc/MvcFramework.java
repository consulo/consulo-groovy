/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.mvc;

import com.intellij.java.execution.configurations.CommandLineBuilder;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.compiler.CompilerPathsManager;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.compiler.execution.CompileStepBeforeRunNoErrorCheck;
import consulo.component.extension.ExtensionPointName;
import consulo.component.util.ModificationTracker;
import consulo.content.ContentFolderTypeProvider;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.content.library.LibraryKind;
import consulo.dataContext.DataManager;
import consulo.execution.CantRunException;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.ide.IdeView;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.OrderEnumerator;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.PathsList;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
@Deprecated //TODO [VISTALL] rewrite it using ModuleExtensions
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class MvcFramework {
  protected static final ExtensionPointName<MvcFramework> EP_NAME = ExtensionPointName.create(MvcFramework.class);

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcFramework");
  public static final Key<Boolean> CREATE_APP_STRUCTURE = Key.create("CREATE_MVC_APP_STRUCTURE");
  public static final Key<Boolean> UPGRADE = Key.create("UPGRADE");
  @NonNls
  public static final String GROOVY_STARTER_CONF = "/conf/groovy-starter.conf";
  @NonNls
  public static final String XMX_JVM_PARAMETER = "-Xmx";

  public abstract boolean hasSupport(@Nonnull Module module);

  public boolean isAuxModule(@Nonnull Module module) {
    return isCommonPluginsModule(module) || isGlobalPluginModule(module);
  }


  public boolean hasFrameworkJar(@Nonnull Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
    return JavaPsiFacade.getInstance(module.getProject()).findClass(getSomeFrameworkClass(), scope) != null;
  }

  public boolean isCommonPluginsModule(@Nonnull Module module) {
    return module.getName().endsWith(getCommonPluginSuffix());
  }

  public List<Module> reorderModulesForMvcView(List<Module> modules) {
    return modules;
  }

  public abstract String getApplicationDirectoryName();

  public void syncSdkAndLibrariesInPluginsModule(@Nonnull Module module) {
    final Module pluginsModule = findCommonPluginsModule(module);
    if (pluginsModule != null) {
      MvcModuleStructureUtil.syncAuxModuleSdk(module, pluginsModule, this);
    }
  }

  public boolean isInteractiveConsoleSupported(@Nonnull Module module) {
    return false;
  }

  public void runInteractiveConsole(@Nonnull Module module) {
    throw new UnsupportedOperationException();
  }

  public abstract void upgradeFramework(@Nonnull Module module);

  public void createApplicationIfNeeded(@Nonnull final Module module) {
    if (findAppRoot(module) == null && module.getUserData(CREATE_APP_STRUCTURE) == Boolean.TRUE) {
      while (ModuleUtilCore.getSdk(module, JavaModuleExtension.class) == null) {
        if (Messages.showYesNoDialog(module.getProject(),
                                     "Cannot generate " + getDisplayName() + " project structure because JDK is not specified for module \"" +
                                       module.getName() + "\".\n" +
                                       getDisplayName() + " project will not be created if you don't specify JDK.\nDo you want to specify JDK?",
                                     "Error",
                                     Messages.getErrorIcon()) == 1) {
          return;
        }
        ShowSettingsUtil.getInstance()
                        .showProjectStructureDialog(module.getProject(),
                                                    projectStructureSelector -> projectStructureSelector.selectOrderEntry(module, null));
      }
      module.putUserData(CREATE_APP_STRUCTURE, null);
      final GeneralCommandLine commandLine = getCreationCommandLine(module);
      if (commandLine == null) {
        return;
      }

      MvcConsole.executeProcess(module, commandLine, new Runnable() {
        public void run() {
          VirtualFile root = findAppRoot(module);
          if (root == null) {
            return;
          }

          PsiDirectory psiDir = PsiManager.getInstance(module.getProject()).findDirectory(root);
          IdeView ide = DataManager.getInstance().getDataContext().getData(IdeView.KEY);
          if (ide != null) {
            ide.selectElement(psiDir);
          }

          //also here comes fileCreated(application.properties) which manages roots and run configuration
        }
      }, true);
    }

  }

  @Nullable
  protected GeneralCommandLine getCreationCommandLine(Module module) {
    String message = "Create default " + getDisplayName() + " directory structure in module '" + module.getName() + "'?";
    final int result = Messages.showDialog(module.getProject(), message, "Create " + getDisplayName() + " application",
                                           new String[]{
                                             "Run 'create-&app'",
                                             "Run 'create-&plugin'",
                                             "&Cancel"
                                           }, 0, getIcon());
    if (result < 0 || result > 1) {
      return null;
    }

    return createCommandAndShowErrors(null, module, true, new MvcCommand(result == 0 ? "create-app" : "create-plugin"));
  }

  public abstract void updateProjectStructure(@Nonnull final Module module);

  public abstract void ensureRunConfigurationExists(@Nonnull Module module);

  @Nullable
  public VirtualFile findAppRoot(@Nullable Module module) {
    if (module == null) {
      return null;
    }

    String appDirName = getApplicationDirectoryName();

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild(appDirName) != null) {
        return root;
      }
    }

    return null;
  }

  @Nullable
  public VirtualFile findAppRoot(@Nullable PsiElement element) {
    VirtualFile appDirectory = findAppDirectory(element);
    return appDirectory == null ? null : appDirectory.getParent();
  }

  @Nullable
  public VirtualFile findAppDirectory(@Nullable Module module) {
    if (module == null) {
      return null;
    }

    String appDirName = getApplicationDirectoryName();

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      VirtualFile res = root.findChild(appDirName);
      if (res != null) {
        return res;
      }
    }

    return null;
  }

  @Nullable
  public VirtualFile findAppDirectory(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }

    PsiFile containingFile = element.getContainingFile().getOriginalFile();
    VirtualFile file = containingFile.getVirtualFile();
    if (file == null) {
      return null;
    }

    ProjectFileIndex index = ProjectRootManager.getInstance(containingFile.getProject()).getFileIndex();

    VirtualFile root = index.getContentRootForFile(file);
    if (root == null) {
      return null;
    }

    return root.findChild(getApplicationDirectoryName());
  }

  @Nullable
  public abstract VirtualFile getSdkRoot(@Nullable Module module);

  public abstract String getUserLibraryName();

  protected List<File> getImplicitClasspathRoots(@Nonnull Module module) {
    final List<File> toExclude = new ArrayList<File>();

    VirtualFile sdkRoot = getSdkRoot(module);
    if (sdkRoot != null) {
      toExclude.add(VfsUtil.virtualToIoFile(sdkRoot));
    }

    ContainerUtil.addIfNotNull(toExclude, getCommonPluginsDir(module));
    final VirtualFile appRoot = findAppRoot(module);
    if (appRoot != null) {
      VirtualFile pluginDir = appRoot.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY);
      if (pluginDir != null) {
        toExclude.add(VfsUtil.virtualToIoFile(pluginDir));
      }


      VirtualFile libDir = appRoot.findChild("lib");
      if (libDir != null) {
        toExclude.add(VfsUtil.virtualToIoFile(libDir));
      }
    }

    final Library library = MvcModuleStructureUtil.findUserLibrary(module, getUserLibraryName());
    if (library != null) {
      for (VirtualFile file : library.getFiles(BinariesOrderRootType.getInstance())) {
        toExclude.add(VfsUtil.virtualToIoFile(VirtualFilePathUtil.getLocalFile(file)));
      }
    }
    return toExclude;
  }

  private PathsList removeFrameworkStuff(Module module, List<VirtualFile> rootFiles) {
    final List<File> toExclude = getImplicitClasspathRoots(module);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Before removing framework stuff: " + rootFiles);
      LOG.debug("Implicit roots:" + toExclude);
    }

    PathsList scriptClassPath = new PathsList();
    eachRoot:
    for (VirtualFile file : rootFiles) {
      for (final File excluded : toExclude) {
        if (VfsUtil.isAncestor(excluded, VfsUtil.virtualToIoFile(file), false)) {
          continue eachRoot;
        }
      }
      scriptClassPath.add(file);
    }
    return scriptClassPath;
  }

  public PathsList getApplicationClassPath(Module module) {
    final List<VirtualFile> classPath = OrderEnumerator.orderEntries(module).recursively().withoutSdk().getPathsList().getVirtualFiles();

    retainOnlyJarsAndDirectories(classPath);

    removeModuleOutput(module, classPath);

    final Module pluginsModule = findCommonPluginsModule(module);
    if (pluginsModule != null) {
      removeModuleOutput(pluginsModule, classPath);
    }

    return removeFrameworkStuff(module, classPath);
  }

  public abstract boolean updatesWholeProject();

  private static void retainOnlyJarsAndDirectories(List<VirtualFile> woSdk) {
    for (Iterator<VirtualFile> iterator = woSdk.iterator(); iterator.hasNext(); ) {
      VirtualFile file = iterator.next();
      final VirtualFile local = ArchiveVfsUtil.getVirtualFileForJar(file);
      final boolean dir = file.isDirectory();
      final String name = file.getName();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Considering: " + file.getPath() + "; local=" + local + "; dir=" + dir + "; name=" + name);
      }
      if (dir || local != null) {
        continue;
      }
      if (name.endsWith(".jar")) {
        continue;
      }
      LOG.debug("Removing");
      iterator.remove();
    }
  }

  private static void removeModuleOutput(Module module, List<VirtualFile> from) {
    CompilerPathsManager compilerPathsManager = CompilerPathsManager.getInstance(module.getProject());
    for (ContentFolderTypeProvider contentFolderType : ContentFolderTypeProvider.filter(LanguageContentFolderScopes.productionAndTest())) {
      from.remove(compilerPathsManager.getCompilerOutput(module, contentFolderType));
    }
  }

  public abstract OwnJavaParameters createJavaParameters(@Nonnull Module module,
                                                         boolean forCreation,
                                                         boolean forTests,
                                                         boolean classpathFromDependencies,
                                                         @Nullable String jvmParams,
                                                         @Nonnull MvcCommand command) throws ExecutionException;

  protected static void ensureRunConfigurationExists(Module module, ConfigurationType configurationType, String name) {
    final RunManager runManager = RunManager.getInstance(module.getProject());
    for (final RunConfiguration runConfiguration : runManager.getConfigurations(configurationType)) {
      if (runConfiguration instanceof MvcRunConfiguration && ((MvcRunConfiguration)runConfiguration).getModule() == module) {
        return;
      }
    }

    final ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    final RunnerAndConfigurationSettings runSettings = runManager.createRunConfiguration(name,
                                                                                         factory);
    final MvcRunConfiguration configuration = (MvcRunConfiguration)runSettings.getConfiguration();
    configuration.setModule(module);
    runManager.addConfiguration(runSettings, false);
    runManager.setSelectedConfiguration(runSettings);

    RunManager.getInstance(module.getProject()).disableTasks(configuration, CompileStepBeforeRun.ID, CompileStepBeforeRunNoErrorCheck.ID);
  }

  public abstract String getFrameworkName();

  public String getDisplayName() {
    return getFrameworkName();
  }

  public abstract Image getIcon(); // 16*16

  public abstract Image getToolWindowIcon(); // 13*13

  public abstract String getSdkHomePropertyName();

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@Nonnull Module module, @Nonnull String command, String... args) {
    return createCommandAndShowErrors(null, module, new MvcCommand(command, args));
  }

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@Nonnull Module module, @Nonnull MvcCommand command) {
    return createCommandAndShowErrors(null, module, command);
  }

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@Nullable String vmOptions, @Nonnull Module module, @Nonnull MvcCommand command) {
    return createCommandAndShowErrors(vmOptions, module, false, command);
  }

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@Nullable String vmOptions,
                                                       @Nonnull Module module,
                                                       final boolean forCreation,
                                                       @Nonnull MvcCommand command) {
    try {
      return createCommand(module, vmOptions, forCreation, command);
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to run grails command: " + command);
      return null;
    }
  }

  @Nonnull
  public GeneralCommandLine createCommand(@Nonnull Module module,
                                          @Nullable String jvmParams,
                                          boolean forCreation,
                                          @Nonnull MvcCommand command) throws ExecutionException {
    final OwnJavaParameters params = createJavaParameters(module, forCreation, false, true, jvmParams, command);
    addJavaHome(params, module);

    final GeneralCommandLine commandLine = createCommandLine(params);

    final VirtualFile griffonHome = getSdkRoot(module);
    if (griffonHome != null) {
      commandLine.getEnvironment().put(getSdkHomePropertyName(), FileUtil.toSystemDependentName(griffonHome.getPath()));
    }

    final VirtualFile root = findAppRoot(module);
    final File ioRoot =
      root != null ? VfsUtilCore.virtualToIoFile(root) : new File(module.getModuleDirPath());
    commandLine.setWorkDirectory(forCreation ? ioRoot.getParentFile() : ioRoot);

    return commandLine;
  }

  public static void addJavaHome(@Nonnull OwnJavaParameters params, @Nonnull Module module) {
    final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    if (sdk != null) {
      String path = StringUtil.trimEnd(sdk.getHomePath(), File.separator);
      if (StringUtil.isNotEmpty(path)) {
        Map<String, String> env = params.getEnv();
        if (env == null) {
          env = new HashMap<String, String>();
          params.setEnv(env);
        }

        env.put("JAVA_HOME", FileUtil.toSystemDependentName(path));
      }
    }
  }

  public static GeneralCommandLine createCommandLine(@Nonnull OwnJavaParameters params) throws CantRunException {
    return CommandLineBuilder.createFromJavaParameters(params);
  }

  private void extractPlugins(Project project, @Nullable VirtualFile pluginRoot, Map<String, VirtualFile> res) {
    if (pluginRoot != null) {
      VirtualFile[] children = pluginRoot.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
          String pluginName = getInstalledPluginNameByPath(project, child);
          if (pluginName != null) {
            res.put(pluginName, child);
          }
        }
      }
    }
  }

  public Collection<VirtualFile> getAllPluginRoots(@Nonnull Module module, boolean refresh) {
    return getCommonPluginRoots(module, refresh);
  }

  public void collectCommonPluginRoots(Map<String, VirtualFile> result, @Nonnull Module module, boolean refresh) {
    if (isCommonPluginsModule(module)) {
      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        String pluginName = getInstalledPluginNameByPath(module.getProject(), root);
        if (pluginName != null) {
          result.put(pluginName, root);
        }
      }
    }
    else {
      VirtualFile root = findAppRoot(module);
      if (root == null) {
        return;
      }

      extractPlugins(module.getProject(), root.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY), result);
      extractPlugins(module.getProject(), MvcModuleStructureUtil.findFile(getCommonPluginsDir(module), refresh), result);
      extractPlugins(module.getProject(), MvcModuleStructureUtil.findFile(getGlobalPluginsDir(module), refresh), result);
    }
  }

  public Collection<VirtualFile> getCommonPluginRoots(@Nonnull Module module, boolean refresh) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();
    collectCommonPluginRoots(result, module, refresh);
    return result.values();
  }

  @Nullable
  public Module findCommonPluginsModule(@Nonnull Module module) {
    return ModuleManager.getInstance(module.getProject()).findModuleByName(getCommonPluginsModuleName(module));
  }

  public boolean isGlobalPluginModule(@Nonnull Module module) {
    return module.getName().startsWith(getGlobalPluginsModuleName());
  }

  @Nullable
  public File getSdkWorkDir(@Nonnull Module module) {
    return getDefaultSdkWorkDir(module);
  }

  @Nullable
  public abstract File getDefaultSdkWorkDir(@Nonnull Module module);

  @Nullable
  public File getGlobalPluginsDir(@Nonnull Module module) {
    final File sdkWorkDir = getSdkWorkDir(module);
    return sdkWorkDir == null ? null : new File(sdkWorkDir, "global-plugins");
  }

  @Nullable
  public File getCommonPluginsDir(@Nonnull Module module) {
    final File grailsWorkDir = getSdkWorkDir(module);
    if (grailsWorkDir == null) {
      return null;
    }

    final String applicationName = getApplicationName(module);
    if (applicationName == null) {
      return null;
    }

    return new File(grailsWorkDir, "projects/" + applicationName + "/plugins");
  }

  public String getApplicationName(Module module) {
    final VirtualFile root = findAppRoot(module);
    if (root == null) {
      return null;
    }
    return root.getName();
  }

  protected abstract String getCommonPluginSuffix();

  public abstract String getGlobalPluginsModuleName();

  public String getCommonPluginsModuleName(Module module) {
    return module.getName() + getCommonPluginSuffix();
  }

  public abstract boolean isSDKLibrary(Library library);

  public abstract MvcProjectStructure createProjectStructure(@Nonnull Module module, boolean auxModule);

  public abstract LibraryKind getLibraryKind();

  public abstract String getSomeFrameworkClass();

  public static void addAvailableSystemScripts(final Collection<String> result, @Nonnull Module module) {
    VirtualFile scriptRoot = null;

    GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);

    for (PsiClass aClass : JavaPsiFacade.getInstance(module.getProject()).findClasses("CreateApp_", searchScope)) {
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null && GroovyCommonClassNames.GROOVY_LANG_SCRIPT.equals(superClass.getQualifiedName())) {
        PsiFile psiFile = aClass.getContainingFile();
        if (psiFile != null) {
          VirtualFile file = psiFile.getVirtualFile();
          if (file != null && file.getFileSystem() instanceof ArchiveFileSystem) {
            VirtualFile parent = file.getParent();
            if (parent != null && parent.findChild("Console.class") != null) {
              scriptRoot = parent;
              break;
            }
          }
        }
      }
    }

    if (scriptRoot == null) {
      return;
    }

    Pattern scriptPattern = Pattern.compile("([A-Za-z0-9]+)_?\\.class");

    for (VirtualFile file : scriptRoot.getChildren()) {
      Matcher matcher = scriptPattern.matcher(file.getName());
      if (matcher.matches()) {
        result.add(GroovyNamesUtil.camelToSnake(matcher.group(1)));
      }
    }

  }

  public abstract boolean isToReformatOnCreation(VirtualFile file);

  public static void addAvailableScripts(final Collection<String> result, @Nullable final VirtualFile root) {
    if (root == null || !root.isDirectory()) {
      return;
    }

    final VirtualFile scripts = root.findChild("scripts");

    if (scripts == null || !scripts.isDirectory()) {
      return;
    }

    for (VirtualFile child : scripts.getChildren()) {
      if (isScriptFile(child)) {
        result.add(GroovyNamesUtil.camelToSnake(child.getNameWithoutExtension()));
      }
    }
  }

  @Nullable
  public static MvcFramework findCommonPluginModuleFramework(Module module) {
    for (MvcFramework framework : EP_NAME.getExtensions()) {
      if (framework.isCommonPluginsModule(module)) {
        return framework;
      }
    }
    return null;
  }

  public static boolean isScriptFileName(String fileName) {
    return fileName.endsWith(GroovyFileType.DEFAULT_EXTENSION) && fileName.charAt(0) != '_';
  }

  private static boolean isScriptFile(VirtualFile virtualFile) {
    return !virtualFile.isDirectory() && isScriptFileName(virtualFile.getName());
  }

  @Nullable
  public String getInstalledPluginNameByPath(Project project, @Nonnull VirtualFile pluginPath) {
    VirtualFile pluginXml = pluginPath.findChild("plugin.xml");
    if (pluginXml == null) {
      return null;
    }

    PsiFile pluginXmlPsi = PsiManager.getInstance(project).findFile(pluginXml);
    if (!(pluginXmlPsi instanceof XmlFile)) {
      return null;
    }

    XmlTag rootTag = ((XmlFile)pluginXmlPsi).getRootTag();
    if (rootTag == null || !"plugin".equals(rootTag.getName())) {
      return null;
    }

    XmlAttribute attrName = rootTag.getAttribute("name");
    if (attrName == null) {
      return null;
    }

    String res = attrName.getValue();
    if (res == null) {
      return null;
    }

    res = res.trim();
    if (res.length() == 0) {
      return null;
    }

    return res;
  }

  @Nullable
  public static MvcFramework getInstance(@Nullable final Module module) {
    if (module == null) {
      return null;
    }

    final Project project = module.getProject();

    return CachedValuesManager.getManager(project).getCachedValue(module, new CachedValueProvider<MvcFramework>() {
      @Override
      public Result<MvcFramework> compute() {
        final ModificationTracker tracker = MvcModuleStructureSynchronizer.getInstance(project).getFileAndRootsModificationTracker();
        for (final MvcFramework framework : EP_NAME.getExtensions()) {
          if (framework.hasSupport(module)) {
            return Result.create(framework, tracker);
          }
        }
        return Result.create(null, tracker);

      }
    });
  }

  @Nullable
  public static MvcFramework getInstanceBySdk(@Nonnull Module module) {
    for (final MvcFramework framework : EP_NAME.getExtensions()) {
      if (framework.getSdkRoot(module) != null) {
        return framework;
      }
    }
    return null;
  }

}
