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

import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.editor.CommonDataKeys;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.dataholder.Key;
import consulo.util.lang.function.Condition;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class GroovyShellActionBase extends AnAction {
  private final GroovyShellConfig myConfig;

  private final Condition<Module> APPLICABLE_MODULE = new Condition<Module>() {
    @Override
    public boolean value(Module module) {
      return myConfig.isSuitableModule(module);
    }
  };

  // non-static to distinguish different module acceptability conditions
  private final Key<CachedValue<Boolean>> APPLICABLE_MODULE_CACHE = Key.create("APPLICABLE_MODULE_CACHE");

  private final Function<Module, String> VERSION_PROVIDER = new Function<Module, String>() {
    @Override
    public String apply(Module module) {
      return myConfig.getVersion(module);
    }
  };

  private final Consumer<Module> RUNNER = new Consumer<Module>() {
    @Override
    public void accept(final Module module) {
      GroovyShellRunnerImpl.doRunShell(myConfig, module);
    }
  };

  public GroovyShellActionBase(GroovyShellConfig runner) {
    myConfig = runner;
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    boolean enabled = project != null && hasGroovyCompatibleModule(project);

    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  private boolean hasGroovyCompatibleModule(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, APPLICABLE_MODULE_CACHE, () -> {
      Collection<Module> possibleModules = myConfig.getPossiblySuitableModules(project);
      return CachedValueProvider.Result.create(ModuleChooserUtil.hasGroovyCompatibleModules(possibleModules, APPLICABLE_MODULE),
                                               ProjectRootModificationTracker.getInstance(project));
    }, false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;
    Collection<Module> suitableModules =
      ModuleChooserUtil.filterGroovyCompatibleModules(myConfig.getPossiblySuitableModules(project), APPLICABLE_MODULE);
    ModuleChooserUtil.selectModule(project, suitableModules, VERSION_PROVIDER, RUNNER);
  }
}