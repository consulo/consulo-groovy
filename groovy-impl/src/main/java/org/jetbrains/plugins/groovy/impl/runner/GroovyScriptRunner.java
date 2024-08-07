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
package org.jetbrains.plugins.groovy.impl.runner;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.configuration.RunProfile;
import consulo.execution.executor.Executor;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.module.Module;
import consulo.module.content.layer.OrderEnumerator;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.PathsList;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class GroovyScriptRunner {
  public abstract boolean isValidModule(@Nonnull Module module);

  public abstract boolean ensureRunnerConfigured(@Nullable Module module,
                                                 RunProfile profile,
                                                 Executor executor,
                                                 final Project project) throws ExecutionException;

  public abstract void configureCommandLine(OwnJavaParameters params,
                                            @Nullable Module module,
                                            boolean tests,
                                            VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws
    CantRunException;

  public boolean shouldRefreshAfterFinish() {
    return false;
  }

  protected static String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    return getPathInConf("groovy-starter.conf");
  }

  public static String getPathInConf(String fileName) {
    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyScriptRunner.class);
      if (new File(jarPath).isFile()) { //jar; distribution mode
        return new File(jarPath, "../" + fileName).getCanonicalPath();
      }

      //else, it's directory in out, development mode
      return new File(jarPath, "conf" + fileName).getCanonicalPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void setGroovyHome(OwnJavaParameters params, @Nonnull String groovyHome) {
    params.getVMParametersList().add("-Dgroovy.home=" + groovyHome);
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().add("-Dgrails.home=" + groovyHome);
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().add("-Dgriffon.home=" + groovyHome);
    }
  }

  protected static void setToolsJar(OwnJavaParameters params) {
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add("-Dtools.jar=" + toolsPath);
      }
    }
  }

  @Nullable
  protected static VirtualFile findGroovyJar(@Nonnull Module module) {
    final VirtualFile[] files = OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots();
    for (VirtualFile root : files) {
      if (root.getName().matches(GroovyConfigUtils.GROOVY_JAR_PATTERN) || GroovyConfigUtils.matchesGroovyAll(root.getName())) {
        return root;
      }
    }
    for (VirtualFile file : files) {
      if (file.getName().contains("groovy") && "jar".equals(file.getExtension())) {
        return file;
      }
    }
    return null;
  }

  protected static void addClasspathFromRootModel(@Nullable Module module,
                                                  boolean isTests,
                                                  OwnJavaParameters params,
                                                  boolean allowDuplication) throws CantRunException {
    PathsList nonCore = getClassPathFromRootModel(module, isTests, params, allowDuplication);
    if (nonCore == null) {
      return;
    }

    final String cp = nonCore.getPathsString();
    if (!StringUtil.isEmptyOrSpaces(cp)) {
      params.getProgramParametersList().add("--classpath");
      params.getProgramParametersList().add(cp);
    }
  }

  @Nullable
  public static PathsList getClassPathFromRootModel(Module module,
                                                    boolean isTests,
                                                    OwnJavaParameters params,
                                                    boolean allowDuplication) throws CantRunException {
    if (module == null) {
      return null;
    }

    final OwnJavaParameters tmp = new OwnJavaParameters();
    tmp.configureByModule(module, isTests ? OwnJavaParameters.CLASSES_AND_TESTS : OwnJavaParameters.CLASSES_ONLY);
    if (tmp.getClassPath().getVirtualFiles().isEmpty()) {
      return null;
    }

    Set<VirtualFile> core = new HashSet<VirtualFile>(params.getClassPath().getVirtualFiles());

    PathsList nonCore = new PathsList();
    for (VirtualFile virtualFile : tmp.getClassPath().getVirtualFiles()) {
      if (allowDuplication || !core.contains(virtualFile)) {
        nonCore.add(virtualFile);
      }
    }
    return nonCore;
  }
}
