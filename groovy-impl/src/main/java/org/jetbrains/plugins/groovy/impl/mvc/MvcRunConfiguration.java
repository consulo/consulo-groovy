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

package org.jetbrains.plugins.groovy.impl.mvc;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.impl.JavaRunConfigurationExtensionManager;
import com.intellij.java.execution.impl.RunConfigurationExtension;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.*;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.util.ProgramParametersUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.JDOMExternalizer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MvcRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements CommonJavaRunConfigurationParameters {
  protected final MvcFramework myFramework;

  public MvcRunConfiguration(String name,
                             RunConfigurationModule configurationModule,
                             ConfigurationFactory factory,
                             MvcFramework framework) {
    super(name, configurationModule, factory);
    myFramework = framework;
  }

  @Override
  protected Class<MvcRunConfigurationOptions> getOptionsClass() {
    return MvcRunConfigurationOptions.class;
  }

  @Override
  public MvcRunConfigurationOptions getOptions() {
    return (MvcRunConfigurationOptions)super.getOptions();
  }

  @Override
  protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
    super.convertLegacyState(converter);

    Element element = converter.getElement();
    ProjectPathMacroManager.getInstance(getProject()).expandPaths(element);

    MvcRunConfigurationOptions options = new MvcRunConfigurationOptions();
    options.setDepsClasspath(!"false".equals(JDOMExternalizer.readString(element, "depsClasspath")));
    converter.setOptions(options);

    String passParentEnv = JDOMExternalizer.readString(element, "passParentEnv");
    converter.addFragment(new ProgramParametersFragmentState(JDOMExternalizer.readString(element, "cmdLine")));
    converter.addFragment(new EnvironmentFragmentState(converter.readEnvs(), StringUtil.isEmpty(passParentEnv) || Boolean.parseBoolean(passParentEnv)));
    converter.addFragment(new VmParametersFragmentState(JDOMExternalizer.readString(element, "vmparams")));
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    return List.of(new MvcDepsClasspathFragment());
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  public boolean isDepsClasspath() {
    return getOptions().isDepsClasspath();
  }

  public void setDepsClasspath(boolean depsClasspath) {
    getOptions().setDepsClasspath(depsClasspath);
  }

  @Override
  @Nullable
  public String getRunClass() {
    return null;
  }

  @Override
  @Nullable
  public String getPackage() {
    return null;
  }

  @Override
  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    List<Module> res = new ArrayList<>();
    for (Module module : modules) {
      if (isSupport(module)) {
        res.add(module);
      }
    }
    return res;
  }

  protected abstract String getNoSdkMessage();

  protected boolean isSupport(@Nonnull Module module) {
    return myFramework.getSdkRoot(module) != null && !myFramework.isAuxModule(module);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    Module module = getModule();
    if (module == null) {
      throw new RuntimeConfigurationException("Module not specified");
    }
    if (module.isDisposed()) {
      throw new RuntimeConfigurationException("Module is disposed");
    }
    if (!isSupport(module)) {
      throw new RuntimeConfigurationException(getNoSdkMessage());
    }
    JavaParametersUtil.checkAlternativeJRE(this);
    super.checkConfiguration();
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
    Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!isSupport(module)) {
      throw new ExecutionException(getNoSdkMessage());
    }

    Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    if (sdk == null) {
      throw CantRunException.noJdkForModule(module);
    }

    JavaCommandLineState state = createCommandLineState(environment, module);
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  protected MvcCommandLineState createCommandLineState(@Nonnull ExecutionEnvironment environment, Module module) {
    return new MvcCommandLineState(environment, getProgramParameters(), module, false);
  }

  public class MvcCommandLineState extends JavaCommandLineState {
    protected final boolean myForTests;

    protected String myCmdLine;

    protected final Module myModule;

    public MvcCommandLineState(@Nonnull ExecutionEnvironment environment, String cmdLine, Module module, boolean forTests) {
      super(environment);
      myModule = module;
      myForTests = forTests;
      myCmdLine = cmdLine;
    }

    public String getCmdLine() {
      return myCmdLine;
    }

    public void setCmdLine(String cmdLine) {
      myCmdLine = cmdLine;
    }

    protected void addEnvVars(OwnJavaParameters params) {
      Map<String, String> envVars = new HashMap<>(getEnvs());

      Map<String, String> oldEnv = params.getEnv();
      if (oldEnv != null) {
        envVars.putAll(oldEnv);
      }

      params.setupEnvs(envVars, isPassParentEnvs());

      MvcFramework.addJavaHome(params, myModule);
    }

    @Override
    protected void buildProcessHandler(@Nonnull ProcessHandlerBuilder builder) throws ExecutionException {
      super.buildProcessHandler(builder);

      builder.shouldDestroyProcessRecursively(true);
    }

    @Override
    protected void setupProcessHandler(@Nonnull ProcessHandler handler) {
      super.setupProcessHandler(handler);
      RunnerSettings runnerSettings = getRunnerSettings();
      JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(MvcRunConfiguration.this, handler, runnerSettings);
    }

    protected final OwnJavaParameters createJavaParameters() throws ExecutionException {
      OwnJavaParameters javaParameters = createJavaParametersMVC();
      for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
        ext.updateJavaParameters(MvcRunConfiguration.this, javaParameters, getRunnerSettings());
      }

      return javaParameters;
    }

    protected OwnJavaParameters createJavaParametersMVC() throws ExecutionException {
      MvcCommand cmd = MvcCommand.parse(myCmdLine);

      OwnJavaParameters params = myFramework.createJavaParameters(myModule, false, myForTests, isDepsClasspath(), getVMParameters(), cmd);

      String alternativeJrePath = getAlternativeJrePath();
      if (alternativeJrePath != null) {
        params.setJdk(JavaParametersUtil.createProjectJdk(getProject(), alternativeJrePath));
      }

      if (!StringUtil.isEmptyOrSpaces(getWorkingDirectory())) {
        params.setWorkingDirectory(ProgramParametersUtil.getWorkingDir(MvcRunConfiguration.this, getProject(), myModule));
      }

      addEnvVars(params);

      return params;
    }

  }

}
