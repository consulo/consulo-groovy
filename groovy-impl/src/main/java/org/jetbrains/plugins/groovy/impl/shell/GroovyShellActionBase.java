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
package org.jetbrains.plugins.groovy.impl.shell;

import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.content.ProjectRootModificationTracker;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.dataholder.Key;
import org.jetbrains.plugins.groovy.impl.util.ModuleChooserUtil;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class GroovyShellActionBase extends AnAction implements AnActionWithAsyncUpdate {
    private final GroovyShellConfig myConfig;

    private final Predicate<Module> APPLICABLE_MODULE = new Predicate<>() {
        @Override
        public boolean test(Module module) {
            return myConfig.isSuitableModule(module);
        }
    };

    // non-static to distinguish different module acceptability conditions
    private final Key<CachedValue<Boolean>> APPLICABLE_MODULE_CACHE = Key.create("APPLICABLE_MODULE_CACHE");

    private final Function<Module, String> VERSION_PROVIDER = new Function<>() {
        @Override
        public String apply(Module module) {
            return myConfig.getVersion(module);
        }
    };

    private final Consumer<Module> RUNNER = new Consumer<>() {
        @Override
        public void accept(Module module) {
            GroovyShellRunnerImpl.doRunShell(myConfig, module);
        }
    };

    public GroovyShellActionBase(GroovyShellConfig runner) {
        myConfig = runner;
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> {
            Project project = e.getData(Project.KEY);
            presentation.setEnabledAndVisible(project != null && hasGroovyCompatibleModule(project));
        }).toCoroutine();
    }

    private boolean hasGroovyCompatibleModule(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, APPLICABLE_MODULE_CACHE, () -> {
            Collection<Module> possibleModules = myConfig.getPossiblySuitableModules(project);
            return CachedValueProvider.Result.create(ModuleChooserUtil.hasGroovyCompatibleModules(possibleModules, APPLICABLE_MODULE),
                ProjectRootModificationTracker.getInstance(project));
        }, false);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        Collection<Module> suitableModules =
            ModuleChooserUtil.filterGroovyCompatibleModules(myConfig.getPossiblySuitableModules(project), APPLICABLE_MODULE);
        ModuleChooserUtil.selectModule(project, suitableModules, VERSION_PROVIDER, RUNNER);
    }
}