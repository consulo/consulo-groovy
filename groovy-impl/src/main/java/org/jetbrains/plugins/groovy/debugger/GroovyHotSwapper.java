package org.jetbrains.plugins.groovy.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.container.boot.ContainerPathManager;
import consulo.container.plugin.PluginManager;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.logging.Logger;
import consulo.platform.Platform;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.regex.Pattern;


/**
 * @author peter
 */
public class GroovyHotSwapper extends JavaProgramPatcher
{
	private static final Logger LOG = Logger.getInstance(GroovyHotSwapper.class);
	private static final String GROOVY_HOTSWAP_AGENT_PATH = "groovy.hotswap.agent.path";

	private static final Pattern SPRING_LOADED_PATTERN = Pattern.compile("-javaagent:.+springloaded-core-[^/\\\\]+\\.jar");

	private static boolean endsWithAny(String s, List<String> endings)
	{
		for(String extension : endings)
		{
			if(s.endsWith(extension))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean containsGroovyClasses(Project project)
	{
		final List<String> extensions = new ArrayList<String>();
		for(String extension : GroovyFileTypeLoader.getAllGroovyExtensions())
		{
			extensions.add("." + extension);
		}
		final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
		for(String fileName : FilenameIndex.getAllFilenames(project))
		{
			if(endsWithAny(fileName, extensions))
			{
				if(!FilenameIndex.getVirtualFilesByName(project, fileName, scope).isEmpty())
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean hasSpringLoadedReloader(OwnJavaParameters javaParameters)
	{
		for(String param : javaParameters.getVMParametersList().getParameters())
		{
			if(SPRING_LOADED_PATTERN.matcher(param).matches())
			{
				return true;
			}
		}

		return false;
	}

	public void patchJavaParameters(Executor executor, RunProfile configuration, OwnJavaParameters javaParameters)
	{
		if(!executor.getId().equals(DefaultDebugExecutor.EXECUTOR_ID))
		{
			return;
		}

		if(!GroovyDebuggerSettings.getInstance().ENABLE_GROOVY_HOTSWAP)
		{
			return;
		}

		if(hasSpringLoadedReloader(javaParameters))
		{
			return;
		}

		if(!(configuration instanceof RunConfiguration))
		{
			return;
		}

		final Project project = ((RunConfiguration) configuration).getProject();
		if(project == null)
		{
			return;
		}

		if(configuration instanceof ModuleRunProfile)
		{
			Module[] modulesInConfiguration = ((ModuleRunProfile) configuration).getModules();
			final Module module = modulesInConfiguration.length == 0 ? null : modulesInConfiguration[0];
			if(module != null)
			{
				final JavaModuleExtension extension = ModuleUtilCore.getExtension(module, JavaModuleExtension.class);
				final LanguageLevel level = extension == null ? null : extension.getLanguageLevel();
				if(level != null && !level.isAtLeast(LanguageLevel.JDK_1_5))
				{
					return;
				}

				if(ModuleUtilCore.getExtension(module, GroovyModuleExtension.class) == null)
				{
					return;
				}
			}
		}

		Sdk jdk = javaParameters.getJdk();
		if(jdk != null)
		{
			String vendor = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VENDOR);
			if(vendor != null && vendor.contains("IBM"))
			{
				LOG.info("Due to IBM JDK pecularities (IDEA-59070) we don't add groovy agent when running applications under it");
				return;
			}
		}

		if(!project.isDefault() && containsGroovyClasses(project))
		{
			final String agentPath = handleSpacesInPath(getAgentJarPath());
			if(agentPath != null)
			{
				javaParameters.getVMParametersList().add("-javaagent:" + agentPath);
			}
		}
	}

	@javax.annotation.Nullable
	private static String handleSpacesInPath(String agentPath)
	{
		if(agentPath.contains(" "))
		{
			final File dir = new File(ContainerPathManager.get().getSystemPath(), "groovyHotSwap");
			if(dir.getAbsolutePath().contains(" "))
			{
				LOG.info("Groovy hot-swap not used since the agent path contains spaces: " + agentPath + "\n" +
						"One can move the agent to a directory with no spaces in path and specify its path in <Consulo dist>/bin/idea.properties as " + GROOVY_HOTSWAP_AGENT_PATH + "=<path>");
				return null;
			}

			final File toFile = new File(dir, "gragent.jar");
			try
			{
				FileUtil.copy(new File(agentPath), toFile);
				return toFile.getPath();
			}
			catch(IOException e)
			{
				LOG.info(e);
			}
		}
		return agentPath;
	}

	private static String getAgentJarPath()
	{
		final String userDefined = Platform.current().jvm().getRuntimeProperty(GROOVY_HOTSWAP_AGENT_PATH);
		if(userDefined != null && new File(userDefined).exists())
		{
			return userDefined;
		}

		return FileUtil.toSystemDependentName(new File(PluginManager.getPluginPath(GroovyHotSwapper.class), "gragent.jar").getPath());
	}
}
