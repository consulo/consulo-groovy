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

package org.jetbrains.plugins.groovy.impl.compiler;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.compiler.ModuleChunk;
import consulo.component.extension.ExtensionPointName;
import consulo.virtualFileSystem.util.PathsList;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GroovyCompilerExtension {
  public static final ExtensionPointName<GroovyCompilerExtension> EP_NAME = ExtensionPointName.create(GroovyCompilerExtension.class);

  public abstract void enhanceCompilationClassPath(@Nonnull ModuleChunk chunk, @Nonnull PathsList classPath);

  @Nonnull
  public abstract List<String> getCompilationUnitPatchers(@Nonnull ModuleChunk chunk);

}
