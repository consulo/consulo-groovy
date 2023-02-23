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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GrMethodComparator {
  private static final ExtensionPointName<GrMethodComparator> EP_NAME = ExtensionPointName.create(GrMethodComparator.class);

  public interface Context {
    @Nullable
    PsiType[] getArgumentTypes();

    @Nullable
    PsiType[] getTypeArguments();

    @Nullable
    PsiType getThisType();

    @Nonnull
    PsiElement getPlace();
  }

  public abstract Boolean dominated(@Nonnull PsiMethod method1,
                                    @Nonnull PsiSubstitutor substitutor1,
                                    @Nonnull PsiMethod method2,
                                    @Nonnull PsiSubstitutor substitutor2,
                                    @Nonnull Context context);

  @Nullable
  public static Boolean checkDominated(@Nonnull PsiMethod method1,
                                       @Nonnull PsiSubstitutor substitutor1,
                                       @Nonnull PsiMethod method2,
                                       @Nonnull PsiSubstitutor substitutor2,
                                       @Nonnull Context context) {
    for (GrMethodComparator comparator : EP_NAME.getExtensionList()) {
      Boolean result = comparator.dominated(method1, substitutor1, method2, substitutor2, context);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
