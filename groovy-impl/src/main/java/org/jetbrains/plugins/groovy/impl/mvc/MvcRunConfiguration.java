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
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.*;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizer;
import consulo.util.xml.serializer.WriteExternalException;
import jakarta.annotation.Nonnull;
import org.jdom.Element;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public abstract class MvcRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements CommonJavaRunConfigurationParameters {
  public String vmParams;
  public String cmdLine;
  public boolean depsClasspath = true;
  protected final MvcFramework myFramework;
  public final Map<String, String> envs = new HashMap<String, String>();
  public boolean passParentEnv = true;

  public MvcRunConfiguration(final String name,
                             final RunConfigurationModule configurationModule,
                             final ConfigurationFactory factory,
                             MvcFramework framework) {
    super(name, configurationModule, factory);
    myFramework = framework;
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  public String getVMParameters() {
    return vmParams;
  }

  public void setVMParameters(String vmParams) {
    this.vmParams = vmParams;
  }

  public void setProgramParameters(@Nullable String value) {
    cmdLine = value;
  }

  @Nullable
  public String getProgramParameters() {
    return cmdLine;
  }

  public void setWorkingDirectory(@Nullable String value) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getWorkingDirectory() {
    return null;
  }

  public void setEnvs(@Nonnull Map<String, String> envs) {
    this.envs.clear();
    this.envs.putAll(envs);
  }

  @Nonnull
  public Map<String, String> getEnvs() {
    return envs;
  }

  public void setPassParentEnvs(boolean passParentEnv) {
    this.passParentEnv = passParentEnv;
  }

  public boolean isPassParentEnvs() {
    return passParentEnv;
  }

  public boolean isAlternativeJrePathEnabled() {
    return false;
  }

  public void setAlternativeJrePathEnabled(boolean enabled) {
    throw new UnsupportedOperationException();
  }

  public String getAlternativeJrePath() {
    return null;
  }

  public void setAlternativeJrePath(String path) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getRunClass() {
    return null;
  }

  @Nullable
  public String getPackage() {
    return null;
  }


  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (isSupport(module)) {
        res.add(module);
      }
    }
    return res;
  }

  public void readExternal(Element element) throws InvalidDataException {
    ProjectPathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    readModule(element);
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    cmdLine = JDOMExternalizer.readString(element, "cmdLine");

    String sPassParentEnviroment = JDOMExternalizer.readString(element, "passParentEnv");
    passParentEnv = StringUtil.isEmpty(sPassParentEnviroment) ? true : Boolean.parseBoolean(sPassParentEnviroment);

    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");

    JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);

    depsClasspath = !"false".equals(JDOMExternalizer.readString(element, "depsClasspath"));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "cmdLine", cmdLine);
    JDOMExternalizer.write(element, "depsClasspath", depsClasspath);
    JDOMExternalizer.writeMap(element, envs, null, "env");
    JDOMExternalizer.write(element, "passParentEnv", passParentEnv);

    JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);

    ProjectPathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  protected abstract String getNoSdkMessage();

  protected boolean isSupport(@Nonnull Module module) {
    return myFramework.getSdkRoot(module) != null && !myFramework.isAuxModule(module);
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    final Module module = getModule();
    if (module == null) {
      throw new RuntimeConfigurationException("Module not specified");
    }
    if (module.isDisposed()) {
      throw new RuntimeConfigurationException("Module is disposed");
    }
    if (!isSupport(module)) {
      throw new RuntimeConfigurationException(getNoSdkMessage());
    }
    super.checkConfiguration();
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!isSupport(module)) {
      throw new ExecutionException(getNoSdkMessage());
    }

    final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
    if (sdk == null) {
      throw CantRunException.noJdkForModule(module);
    }

    final JavaCommandLineState state = createCommandLineState(environment, module);
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  protected MvcCommandLineState createCommandLineState(@Nonnull ExecutionEnvironment environment, Module module) {
    return new MvcCommandLineState(environment, cmdLine, module, false);
  }

  public SettingsEditor<? extends MvcRunConfiguration> getConfigurationEditor() {
    return new MvcRunConfigurationEditor<MvcRunConfiguration>();
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

    protected void addEnvVars(final OwnJavaParameters params) {
      Map<String, String> envVars = new HashMap<String, String>(envs);

      Map<String, String> oldEnv = params.getEnv();
      if (oldEnv != null) {
        envVars.putAll(oldEnv);
      }

      params.setupEnvs(envVars, passParentEnv);

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
      final RunnerSettings runnerSettings = getRunnerSettings();
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

      final OwnJavaParameters params = myFramework.createJavaParameters(myModule, false, myForTests, depsClasspath, vmParams, cmd);

      addEnvVars(params);

      return params;
    }

  }

}
