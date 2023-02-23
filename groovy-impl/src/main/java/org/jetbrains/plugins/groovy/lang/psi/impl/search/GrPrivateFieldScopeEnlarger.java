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
package org.jetbrains.plugins.groovy.lang.psi.impl.search;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.content.scope.SearchScope;
import consulo.language.impl.psi.ResolveScopeManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.UseScopeEnlarger;
import consulo.language.psi.scope.GlobalSearchScope;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GrPrivateFieldScopeEnlarger extends UseScopeEnlarger {
  @Override
  public SearchScope getAdditionalUseScope(@Nonnull PsiElement element) {
    if (element instanceof PsiField && ((PsiField)element).hasModifierProperty(PsiModifier.PRIVATE)) {
      final GlobalSearchScope maximalUseScope = ResolveScopeManager.getElementUseScope(element);
      return new GrSourceFilterScope(maximalUseScope);
    }

    return null;
  }
}
