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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
@ExtensionImpl
public class DynamicMembersContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@Nonnull PsiType qualifierType,
                                     PsiClass aClass,
                                     @Nonnull PsiScopeProcessor processor,
                                     @Nonnull PsiElement place,
                                     @Nonnull ResolveState state) {
    if (aClass == null) {
      return;
    }

    final DynamicManager manager = DynamicManager.getInstance(place.getProject());

    for (String qName : ClassUtil.getSuperClassesWithCache(aClass).keySet()) {
      for (PsiMethod method : manager.getMethods(qName)) {
        if (!ResolveUtil.processElement(processor, method, state)) {
          return;
        }
      }

      for (PsiVariable var : manager.getProperties(qName)) {
        if (!ResolveUtil.processElement(processor, var, state)) {
          return;
        }
      }
    }
  }
}
