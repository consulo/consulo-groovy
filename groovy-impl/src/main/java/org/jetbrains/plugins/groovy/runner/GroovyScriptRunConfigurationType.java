/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.runner;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import consulo.groovy.module.extension.GroovyModuleExtension;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import consulo.module.extension.ModuleExtensionHelper;
import icons.JetgroovyIcons;

public class GroovyScriptRunConfigurationType implements ConfigurationType
{
	private final GroovyFactory myConfigurationFactory;

	public GroovyScriptRunConfigurationType()
	{
		myConfigurationFactory = new GroovyFactory(this);
	}

	public String getDisplayName()
	{
		return "Groovy";
	}

	public String getConfigurationTypeDescription()
	{
		return "Groovy Class or Script";
	}

	public Icon getIcon()
	{
		return JetgroovyIcons.Groovy.Groovy_16x16;
	}

	@NonNls
	@Nonnull
	public String getId()
	{
		return "GroovyScriptRunConfiguration";
	}

	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{myConfigurationFactory};
	}

	public static GroovyScriptRunConfigurationType getInstance()
	{
		return ConfigurationTypeUtil.findConfigurationType(GroovyScriptRunConfigurationType.class);
	}

	private static class GroovyFactory extends ConfigurationFactory
	{
		public GroovyFactory(ConfigurationType type)
		{
			super(type);
		}

		@Override
		public boolean isApplicable(@Nonnull Project project)
		{
			return ModuleExtensionHelper.getInstance(project).hasModuleExtension(GroovyModuleExtension.class);
		}

		public RunConfiguration createTemplateConfiguration(Project project)
		{
			return new GroovyScriptRunConfiguration("Groovy Script", project, this);
		}
	}
}
