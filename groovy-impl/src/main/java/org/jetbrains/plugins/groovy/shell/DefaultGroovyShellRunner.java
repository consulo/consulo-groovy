/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.shell;

import com.intellij.java.impl.psi.util.FindClassUtil;
import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Sergey Evdokimov
 */
public class DefaultGroovyShellRunner extends GroovyShellConfig {

  @Nonnull
  @Override
  public String getWorkingDirectory(@Nonnull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots[0].getPath();
  }

  @Nonnull
  @Override
  public OwnJavaParameters createJavaParameters(@Nonnull Module module) throws ExecutionException {
    OwnJavaParameters res = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(res, module, "org.codehaus.groovy.tools.shell.Main", false, true);
    res.setWorkingDirectory(getWorkingDirectory(module));
    return res;
  }

  @Override
  public boolean canRun(@Nonnull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return contentRoots.length > 0 && hasGroovyWithNeededJars(module);
  }

  @Nonnull
  @Override
  public String getVersion(@Nonnull Module module) {
    String homePath = LibrariesUtil.getGroovyHomePath(module);
    assert homePath != null;

    String version = GroovyConfigUtils.getInstance().getSDKVersion(homePath);
    return version == AbstractConfigUtils.UNDEFINED_VERSION ? "" : "Groovy " + version;
  }

  private final static String[] REQUIRED_GROOVY_CLASSES = {
    "org.apache.commons.cli.CommandLineParser",
    "org.codehaus.groovy.tools.shell.Main",
    "org.fusesource.jansi.AnsiConsole"
  };

  public static boolean hasGroovyWithNeededJars(Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    for (String className : REQUIRED_GROOVY_CLASSES) {
      if (facade.findClass(className, scope) == null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isSuitableModule(Module module) {
    return super.isSuitableModule(module) && hasGroovyWithNeededJars(module);
  }

  @Override
  public Collection<Module> getPossiblySuitableModules(Project project) {
    Set<Module> results = null;
    for (String className : REQUIRED_GROOVY_CLASSES) {
      Collection<Module> someModules = FindClassUtil.findModulesWithClass(project, className);
      if (results == null) {
        results = new LinkedHashSet<>(someModules);
      }
      else {
        results.retainAll(someModules);
      }
      if (results.isEmpty()) {
        return List.of();
      }
    }
    return results;
  }

  @Override
  public String getTitle() {
    return "Groovy Shell";
  }
}
