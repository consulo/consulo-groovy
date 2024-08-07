/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.configSlurper;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import jakarta.annotation.Nullable;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Sergey Evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class ConfigSlurperSupport {

  public static final ExtensionPointName<ConfigSlurperSupport> EP_NAME = ExtensionPointName.create(ConfigSlurperSupport.class);

  @Nullable
  public abstract PropertiesProvider getProvider(@Nonnull GroovyFile file);

  @Nullable
  public PropertiesProvider getConfigSlurperInfo(@Nonnull GrExpression qualifier, @Nonnull PsiElement qualifierResolve) {
    return null;
  }

  public interface PropertiesProvider {
    void collectVariants(@Nonnull List<String> prefix, @Nonnull BiConsumer<String, Boolean> consumer);
  }
}
