/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

import javax.annotation.Nullable;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GrVariableEnhancer {
  public static final ExtensionPointName<GrVariableEnhancer> EP_NAME = ExtensionPointName.create(GrVariableEnhancer.class);

  @Nullable
  public abstract PsiType getVariableType(GrVariable variable);

  @Nullable
  public static PsiType getEnhancedType(final GrVariable variable) {
    for (GrVariableEnhancer enhancer : EP_NAME.getExtensions()) {
      final PsiType type = enhancer.getVariableType(variable);
      if (type != null) {
        return type;
      }
    }

    return null;
  }

}
