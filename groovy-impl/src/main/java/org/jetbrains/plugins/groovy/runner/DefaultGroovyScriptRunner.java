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

import consulo.execution.CantRunException;
import consulo.execution.ExecutionUtil;
import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.http.HttpProxyManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.io.CharsetToolkit;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingManager;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.List;

public class DefaultGroovyScriptRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(@Nonnull Module module) {
    return LibrariesUtil.hasGroovySdk(module);
  }

  @Override
  public boolean ensureRunnerConfigured(@Nullable Module module,
                                        RunProfile profile,
                                        Executor executor,
                                        final Project project) throws ExecutionException {
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (LibrariesUtil.getGroovyHomePath(module) == null) {
      ExecutionUtil.handleExecutionError(project, executor.getToolWindowId(), profile, new ExecutionException("Groovy is not configured"));
      ShowSettingsUtil.getInstance()
                      .showProjectStructureDialog(module.getProject(),
                                                  projectStructureSelector -> projectStructureSelector.selectOrderEntry(module, null));
      return false;
    }

    return true;
  }

  @Override
  public void configureCommandLine(OwnJavaParameters params,
                                   @Nullable Module module,
                                   boolean tests,
                                   VirtualFile script,
                                   GroovyScriptRunConfiguration configuration) throws CantRunException {
    configureGenericGroovyRunner(params, module, "groovy.ui.GroovyMain", false, tests);

    addClasspathFromRootModel(module, tests, params, true);

    params.getVMParametersList().addParametersString(configuration.getVMParameters());

    addScriptEncodingSettings(params, script, module);

    if (configuration.isDebugEnabled()) {
      params.getProgramParametersList().add("--debug");
    }

    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.getScriptPath()));
    params.getProgramParametersList().addParametersString(configuration.getScriptParameters());
  }

  public static void configureGenericGroovyRunner(@Nonnull OwnJavaParameters params,
                                                  @Nonnull Module module,
                                                  @Nonnull String mainClass,
                                                  boolean useBundled,
                                                  boolean tests) {
    final VirtualFile groovyJar = findGroovyJar(module);
    if (useBundled) {
      params.getClassPath().add(GroovyUtils.getBundledGroovyJar());
    }
    else if (groovyJar != null) {
      params.getClassPath().add(groovyJar);
    }

    setToolsJar(params);

    String groovyHome = useBundled ? FileUtil.toCanonicalPath(GroovyUtils.getBundledGroovyJar()
                                                                         .getParentFile()
                                                                         .getParent()) : LibrariesUtil.getGroovyHomePath(module);
    if (groovyHome != null) {
      groovyHome = FileUtil.toSystemDependentName(groovyHome);
    }
    if (groovyHome != null) {
      setGroovyHome(params, groovyHome);
    }

    final String confPath = getConfPath(groovyHome);
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);
    List<Pair<String, String>> jvmProperties = HttpProxyManager.getInstance().getJvmProperties(false, null);
    for (Pair<String, String> jvmProperty : jvmProperties) {
      params.getVMParametersList().add(jvmProperty.getKey(), jvmProperty.getValue());
    }

    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(mainClass);

    if (params.getVMParametersList().getPropertyValue(GroovycOSProcessHandler.GRAPE_ROOT) == null) {
      String sysRoot = System.getProperty(GroovycOSProcessHandler.GRAPE_ROOT);
      if (sysRoot != null) {
        params.getVMParametersList().defineProperty(GroovycOSProcessHandler.GRAPE_ROOT, sysRoot);
      }
    }
  }

  private static void addScriptEncodingSettings(final OwnJavaParameters params, final VirtualFile scriptFile, Module module) {
    Charset charset = EncodingProjectManager.getInstance(module.getProject()).getEncoding(scriptFile, true);
    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
      if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), charset)) {
        params.getProgramParametersList().add("--encoding=" + charset.displayName());
      }
    }
    else {
      params.getProgramParametersList().add("--encoding=" + charset.displayName());
    }
  }

}
