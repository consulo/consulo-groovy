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
package org.jetbrains.plugins.groovy.extensions;

import java.util.Collections;
import java.util.List;

import jakarta.annotation.Nonnull;

import consulo.language.psi.scope.GlobalSearchScope;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import consulo.ui.image.Image;

/**
 * @author ilyas
 */
public abstract class GroovyScriptType {

  private final String id;

  protected GroovyScriptType(String id) {
    this.id = id;
  }

  @Nonnull
  public String getId() {
    return id;
  }

  @Nonnull
  public abstract Image getScriptIcon();

  public GlobalSearchScope patchResolveScope(@Nonnull GroovyFile file, @Nonnull GlobalSearchScope baseScope) {
    return baseScope;
  }

  public List<String> appendImplicitImports(@Nonnull GroovyFile file) {
    return Collections.emptyList();
  }

  public boolean shouldBeCompiled(GroovyFile script) {
    return false;
  }
}
