package org.jetbrains.plugins.groovy.debugger;

import com.intellij.java.execution.runners.JavaProgramPatcher;
import com.intellij.java.language.LanguageLevel;
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
import consulo.language.file.FileTypeManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.fileType.FileNameMatcher;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.internal.matcher.ExtensionFileNameMatcher;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * @author peter
 */
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
    final Set<String> extensions = new LinkedHashSet<>();
    for (FileType type : GroovyFileType.getGroovyEnabledFileTypes()) {
      List<FileNameMatcher> associations = FileTypeManager.getInstance().getAssociations(type);
      for (FileNameMatcher association : associations) {
        if (association instanceof ExtensionFileNameMatcher em) {
          extensions.add("." + em.getExtension());
        }
      }
    }

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    for (String fileName : FilenameIndex.getAllFilenames(project)) {
      if (endsWithAny(fileName, extensions)) {
        if (!FilenameIndex.getVirtualFilesByName(project, fileName, scope).isEmpty()) {
          return true;
        }
      }
    }
    return false;
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

    final Project project = ((RunConfiguration)configuration).getProject();
    if (project == null) {
      return;
    }

    if (configuration instanceof ModuleRunProfile) {
      Module[] modulesInConfiguration = ((ModuleRunProfile)configuration).getModules();
      final Module module = modulesInConfiguration.length == 0 ? null : modulesInConfiguration[0];
      if (module != null) {
        final JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
        final LanguageLevel level = extension == null ? null : extension.getLanguageLevel();
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
      final String agentPath = handleSpacesInPath(getAgentJarPath());
      if (agentPath != null) {
        javaParameters.getVMParametersList().add("-javaagent:" + agentPath);
      }
    }
  }

  @Nullable
  private static String handleSpacesInPath(String agentPath) {
    if (agentPath.contains(" ")) {
      final File dir = new File(ContainerPathManager.get().getSystemPath(), "groovyHotSwap");
      if (dir.getAbsolutePath().contains(" ")) {
        LOG.info("Groovy hot-swap not used since the agent path contains spaces: " + agentPath + "\n" +
                   "One can move the agent to a directory with no spaces in path and specify its path in <Consulo dist>/bin/idea.properties as " + GROOVY_HOTSWAP_AGENT_PATH + "=<path>");
        return null;
      }

      final File toFile = new File(dir, "gragent.jar");
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
    final String userDefined = Platform.current().jvm().getRuntimeProperty(GROOVY_HOTSWAP_AGENT_PATH);
    if (userDefined != null && new File(userDefined).exists()) {
      return userDefined;
    }

    return FileUtil.toSystemDependentName(new File(PluginManager.getPluginPath(GroovyHotSwapper.class), "gragent.jar").getPath());
  }
}
