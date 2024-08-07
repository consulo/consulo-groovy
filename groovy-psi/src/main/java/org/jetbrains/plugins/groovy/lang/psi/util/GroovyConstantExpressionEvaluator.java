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
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.java.language.impl.psi.impl.ConstantExpressionEvaluator;
import com.intellij.java.language.psi.PsiConstantEvaluationHelper;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyConstantExpressionEvaluator implements ConstantExpressionEvaluator {

  @Nullable
  public static Object evaluate(@Nullable GrExpression expression) {
    if (expression instanceof GrParenthesizedExpression) {
      return evaluate(((GrParenthesizedExpression)expression).getOperand());
    }
    if (expression instanceof GrLiteral) {
      return ((GrLiteral)expression).getValue();
    }
    return null;
  }

  @Nullable
  public Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow) {
    if (!(expression instanceof GrExpression)) return null;
    return evaluate((GrExpression)expression);
  }

  @Nullable
  public Object computeExpression(PsiElement expression,
                                  boolean throwExceptionOnOverflow,
                                  @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator) {
    if (!(expression instanceof GrExpression)) return null;
    return evaluate((GrExpression)expression);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
