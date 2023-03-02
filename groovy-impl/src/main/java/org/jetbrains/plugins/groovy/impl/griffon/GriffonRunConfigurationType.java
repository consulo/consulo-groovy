/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.impl.griffon;

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.execution.CompileStepBeforeRun;
import consulo.compiler.execution.CompileStepBeforeRunNoErrorCheck;
import consulo.execution.BeforeRunTask;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.groovy.impl.griffon.module.extension.GriffonModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;

import javax.annotation.Nonnull;

@ExtensionImpl
public class GriffonRunConfigurationType implements ConfigurationType {
  private final ConfigurationFactory myConfigurationFactory;
  private static final String GRIFFON_APPLICATION = "Griffon";

  public GriffonRunConfigurationType() {
    myConfigurationFactory = new ConfigurationFactory(this) {
      @Override
      public RunConfiguration createTemplateConfiguration(Project project) {
        return new GriffonRunConfiguration(this, project, GRIFFON_APPLICATION, "run-app");
      }

      @Override
      public boolean isApplicable(@Nonnull Project project) {
        return ModuleExtensionHelper.getInstance(project).hasModuleExtension(GriffonModuleExtension.class);
      }

      @Override
      public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
        if (providerID == CompileStepBeforeRun.ID || providerID == CompileStepBeforeRunNoErrorCheck.ID) {
          task.setEnabled(false);
        }
      }
    };
  }

  public String getDisplayName() {
    return GRIFFON_APPLICATION;
  }

  public String getConfigurationTypeDescription() {
    return GRIFFON_APPLICATION;
  }

  public Image getIcon() {
    return JetgroovyIcons.Griffon.Griffon;
  }

  @NonNls
  @Nonnull
  public String getId() {
    return "GriffonRunConfigurationType";
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[]{myConfigurationFactory};
  }

  public static GriffonRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(GriffonRunConfigurationType.class);
  }

}
