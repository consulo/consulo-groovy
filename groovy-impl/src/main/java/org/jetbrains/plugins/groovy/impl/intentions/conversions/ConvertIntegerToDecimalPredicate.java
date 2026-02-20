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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;


class ConvertIntegerToDecimalPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrLiteral)) {
      return false;
    }
    GrLiteral expression = (GrLiteral) element;
    PsiType type = expression.getType();
    if (type == null) {
      return false;
    }
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type) &&
        !type.equalsToText("java.lang.Integer") && !type.equalsToText("java.lang.Long")) {
      return false;
    }
    @NonNls String text = expression.getText().replaceAll("_", "");
    if (text == null || text.length() < 2) {
      return false;
    }
    if ("0".equals(text) || "0L".equals(text) || "0l".equals(text)) {
      return false;
    }
    return text.charAt(0) == '0';
  }
}
