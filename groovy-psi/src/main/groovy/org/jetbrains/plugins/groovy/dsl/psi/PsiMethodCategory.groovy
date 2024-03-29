/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.dsl.psi

import com.intellij.java.language.psi.PsiClass
import com.intellij.java.language.psi.PsiField
import com.intellij.java.language.psi.PsiMethod
import com.intellij.java.language.psi.PsiType
import consulo.annotation.component.ExtensionImpl
import org.jetbrains.annotations.Nullable

/**
 * @author ilyas
 */
@ExtensionImpl
public class PsiMethodCategory implements PsiEnhancerCategory {

  @Nullable
  public static PsiClass getClassType(PsiField field) {
    final PsiType type = field.getType();
    return PsiCategoryUtil.getClassType(type, field);
  }

  static Map getParamStringVector(PsiMethod method) {
    def Map result = [:]
    int idx = 1
    for (p in method.parameterList.parameters) {
      result.put("value$idx", p.getType().getCanonicalText())
      idx++
    }
    return result;
  }

}
