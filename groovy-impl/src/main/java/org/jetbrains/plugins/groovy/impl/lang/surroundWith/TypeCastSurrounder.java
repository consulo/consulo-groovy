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
package org.jetbrains.plugins.groovy.impl.lang.surroundWith;

import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class TypeCastSurrounder extends GroovyExpressionSurrounder {
  protected TextRange surroundExpression(GrExpression expression, PsiElement context) {
    GrParenthesizedExpression parenthesized = (GrParenthesizedExpression) GroovyPsiElementFactory.getInstance(expression.getProject()).createExpressionFromText("((Type)a)", context);
    GrTypeCastExpression typeCast = (GrTypeCastExpression) parenthesized.getOperand();
    replaceToOldExpression(typeCast.getOperand(), expression);
    GrTypeElement typeElement = typeCast.getCastTypeElement();
    int endOffset = typeElement.getTextRange().getStartOffset() + expression.getTextRange().getStartOffset();
    parenthesized = (GrParenthesizedExpression) expression.replaceWithExpression(parenthesized, false);

    final GrTypeCastExpression newTypeCast = (GrTypeCastExpression)parenthesized.getOperand();
    final GrTypeElement newTypeElement = newTypeCast.getCastTypeElement();
    newTypeElement.delete();
    return new TextRange(endOffset, endOffset);
  }

  public String getTemplateDescription() {
    return "((Type) expr)";
  }
}
