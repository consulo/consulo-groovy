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

package org.jetbrains.plugins.groovy.impl.compiler;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposer;
import jakarta.inject.Singleton;

/**
 * @author peter
 */
@State(name = "GroovyCompilerConfiguration", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class GroovyCompilerWorkspaceConfiguration implements PersistentStateComponent<JpsGroovySettings>, Disposable {
  String myHeapSize = JpsGroovySettings.DEFAULT_HEAP_SIZE;
  boolean myInvokeDynamic = JpsGroovySettings.DEFAULT_INVOKE_DYNAMIC;
  boolean transformsOk = JpsGroovySettings.DEFAULT_TRANSFORMS_OK;
  final ExcludedEntriesConfiguration myExcludeFromStubGeneration = new ExcludedEntriesConfiguration();

  public JpsGroovySettings getState() {
    final JpsGroovySettings bean = new JpsGroovySettings();
    bean.heapSize = myHeapSize;
    bean.invokeDynamic = myInvokeDynamic;
    bean.transformsOk = transformsOk;
    myExcludeFromStubGeneration.writeExternal(bean.excludes);
    return bean;
  }

  public void loadState(JpsGroovySettings state) {
    myHeapSize = state.heapSize;
    myInvokeDynamic = state.invokeDynamic;
    transformsOk = state.transformsOk;

    myExcludeFromStubGeneration.readExternal(state.excludes);
  }

  public void dispose() {
    Disposer.dispose(myExcludeFromStubGeneration);
  }

}
