package org.jetbrains.plugins.groovy.impl.debugger;

import com.intellij.java.execution.runners.JavaProgramPatcher;
import com.intellij.java.language.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginManager;
import consulo.execution.configuration.ModuleRunProfile;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.RunProfile;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.executor.Executor;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FileTypeIndex;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * @author peter
 */
@ExtensionImpl
public class GroovyHotSwapper extends JavaProgramPatcher {
  private static final Logger LOG = Logger.getInstance(GroovyHotSwapper.class);
  private static final String GROOVY_HOTSWAP_AGENT_PATH = "groovy.hotswap.agent.path";

  private static final Pattern SPRING_LOADED_PATTERN = Pattern.compile("-javaagent:.+springloaded-core-[^/\\\\]+\\.jar");

  private static boolean endsWithAny(String s, Set<String> endings) {
    for (String extension : endings) {
      if (s.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsGroovyClasses(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () ->
      CachedValueProvider.Result.create(FileTypeIndex.containsFileOfType(GroovyFileType.GROOVY_FILE_TYPE,
                                                                         GlobalSearchScope.projectScope(project)),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static boolean hasSpringLoadedReloader(OwnJavaParameters javaParameters) {
    for (String param : javaParameters.getVMParametersList().getParameters()) {
      if (SPRING_LOADED_PATTERN.matcher(param).matches()) {
        return true;
      }
    }

    return false;
  }

  public void patchJavaParameters(Executor executor, RunProfile configuration, OwnJavaParameters javaParameters) {
    if (!executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID)) {
      return;
    }

    if (!GroovyDebuggerSettings.getInstance().ENABLE_GROOVY_HOTSWAP) {
      return;
    }

    if (hasSpringLoadedReloader(javaParameters)) {
      return;
    }

    if (!(configuration instanceof RunConfiguration)) {
      return;
    }

    Project project = ((RunConfiguration)configuration).getProject();
    if (project == null) {
      return;
    }

    if (configuration instanceof ModuleRunProfile) {
      Module[] modulesInConfiguration = ((ModuleRunProfile)configuration).getModules();
      Module module = modulesInConfiguration.length == 0 ? null : modulesInConfiguration[0];
      if (module != null) {
        JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
        LanguageLevel level = extension == null ? null : extension.getLanguageLevel();
        if (level != null && !level.isAtLeast(LanguageLevel.JDK_1_5)) {
          return;
        }

        if (ModuleUtilCore.getExtension(module, GroovyModuleExtension.class) == null) {
          return;
        }
      }
    }

//    Sdk jdk = javaParameters.getJdk();
//    if (jdk != null) {
//      String vendor = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VENDOR);
//      if (vendor != null && vendor.contains("IBM")) {
//        LOG.info("Due to IBM JDK pecularities (IDEA-59070) we don't add groovy agent when running applications under it");
//        return;
//      }
//    }

    if (!project.isDefault() && containsGroovyClasses(project)) {
      String agentPath = handleSpacesInPath(getAgentJarPath());
      if (agentPath != null) {
        javaParameters.getVMParametersList().add("-javaagent:" + agentPath);
      }
    }
  }

  @Nullable
  private static String handleSpacesInPath(String agentPath) {
    if (agentPath.contains(" ")) {
      File dir = new File(ContainerPathManager.get().getSystemPath(), "groovyHotSwap");
      if (dir.getAbsolutePath().contains(" ")) {
        LOG.info("Groovy hot-swap not used since the agent path contains spaces: " + agentPath + "\n" +
                   "One can move the agent to a directory with no spaces in path and specify its path in <Consulo dist>/bin/idea.properties as " + GROOVY_HOTSWAP_AGENT_PATH + "=<path>");
        return null;
      }

      File toFile = new File(dir, "gragent.jar");
      try {
        FileUtil.copy(new File(agentPath), toFile, FilePermissionCopier.DISABLED);
        return toFile.getPath();
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return agentPath;
  }

  private static String getAgentJarPath() {
    String userDefined = Platform.current().jvm().getRuntimeProperty(GROOVY_HOTSWAP_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      return userDefined;
    }

    return FileUtil.toSystemDependentName(new File(PluginManager.getPluginPath(GroovyHotSwapper.class), "gragent.jar").getPath());
  }
}
