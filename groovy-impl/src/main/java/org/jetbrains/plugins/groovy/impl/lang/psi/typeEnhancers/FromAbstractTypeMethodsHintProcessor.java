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
package org.jetbrains.plugins.groovy.impl.lang.psi.typeEnhancers;

import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import consulo.util.collection.ContainerUtil;

import jakarta.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by Max Medvedev on 28/02/14
 */
public class FromAbstractTypeMethodsHintProcessor extends SignatureHintProcessor {
  @Override
  public String getHintName() {
    return "groovy.transform.stc.FromAbstractTypeMethods";
  }

  @Nonnull
  @Override
  public List<PsiType[]> inferExpectedSignatures(@Nonnull PsiMethod method,
                                                 @Nonnull PsiSubstitutor substitutor,
                                                 @Nonnull String[] options) {
    if (options.length != 1) return Collections.emptyList();

    String qname = options[0];
    PsiClass aClass = JavaPsiFacade.getInstance(method.getProject()).findClass(qname, method.getResolveScope());
    if (aClass == null) return Collections.emptyList();

    Collection<MethodSignature> abstractSignatures = OverrideImplementExploreUtil.getMethodSignaturesToImplement(aClass);
    return ContainerUtil.map(abstractSignatures, signature -> signature.getParameterTypes());
  }
}
