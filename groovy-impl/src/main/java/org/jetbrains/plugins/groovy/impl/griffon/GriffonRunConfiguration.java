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

import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfigurationModule;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.mvc.MvcRunConfiguration;

public class GriffonRunConfiguration extends MvcRunConfiguration {
  private final ConfigurationFactory myFactory;

  public GriffonRunConfiguration(ConfigurationFactory factory, Project project, String name, String cmdLine) {
    super(name, new RunConfigurationModule(project), factory, GriffonFramework.getInstance());
    myFactory = factory;
    this.cmdLine = cmdLine;
  }

  @Override
  protected String getNoSdkMessage() {
    return "Griffon SDK is not configured";
  }

  protected ModuleBasedConfiguration createInstance() {
    GriffonRunConfiguration res = new GriffonRunConfiguration(myFactory, getConfigurationModule().getProject(), getName(), cmdLine);
    res.envs.putAll(envs);
    res.passParentEnv = passParentEnv;
    return res;
  }

}