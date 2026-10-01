/*
 * Copyright 2013-2026 consulo.io
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

import consulo.application.concurrent.coroutine.ReadLock;
import consulo.disposer.Disposable;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.PseudoComponent;
import consulo.ui.UIAction;
import consulo.ui.ValueComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEventListener;
import consulo.ui.event.ValueComponentEvent;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class RunConfigurationModuleBox implements PseudoComponent {
    private final MutableFlatDataModel<Module> myModel = FlatDataModel.of(new ArrayList<>());
    private final ComboBox<Module> myComboBox;
    private int myGeneration;
    private boolean myUpdating;

    @RequiredUIAccess
    public RunConfigurationModuleBox() {
        myComboBox = ComboBox.create(myModel);
        myComboBox.setRender((presentation, item) -> {
            Module module = item.getValue();
            if (module != null) {
                presentation.withIcon(PlatformIconGroup.nodesModule());
                presentation.append(module.getName());
            }
        });
        myComboBox.setSpeedSearchConverter(module -> module == null ? "" : module.getName());
    }

    @RequiredUIAccess
    @Override
    public Component getComponent() {
        return myComboBox;
    }

    @Nullable
    @RequiredUIAccess
    public Module getValue() {
        return myComboBox.getValue();
    }

    public Disposable addValueListener(ComponentEventListener<ValueComponent<Module>, ValueComponentEvent<Module>> listener) {
        return myComboBox.addValueListener(event -> {
            if (!myUpdating) {
                listener.onEvent(event);
            }
        });
    }

    @RequiredUIAccess
    public void reset(Project project, @Nullable Module module, Supplier<? extends Collection<Module>> validModules) {
        setModules(module == null ? List.of() : List.of(module), module);

        int generation = ++myGeneration;
        CoroutineScope.launchAsync(
            project.coroutineContext(),
            () -> Coroutine
                .first(ReadLock.<Void, List<Module>>apply(ignored -> new ArrayList<>(validModules.get())))
                .then(UIAction.<List<Module>, Void>apply(modules -> {
                    if (generation == myGeneration) {
                        setModules(modules, myComboBox.getValue());
                    }
                    return null;
                }))
        );
    }

    public void cancelLoading() {
        myGeneration++;
    }

    @RequiredUIAccess
    private void setModules(List<Module> modules, @Nullable Module selected) {
        List<Module> items = new ArrayList<>(modules);
        if (selected != null && !items.contains(selected)) {
            items.add(selected);
        }
        myUpdating = true;
        try {
            myModel.replaceAll(items);
            myComboBox.setValue(selected, false);
        }
        finally {
            myUpdating = false;
        }
    }
}
