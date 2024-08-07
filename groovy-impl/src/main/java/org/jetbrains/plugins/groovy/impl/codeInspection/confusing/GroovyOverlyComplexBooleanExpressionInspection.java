/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SingleIntegerFieldOptionsPanel;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import javax.swing.*;

public class GroovyOverlyComplexBooleanExpressionInspection extends BaseInspection {

  private static final int TERM_LIMIT = 3;

  /**
   * @noinspection PublicField,WeakerAccess
   */
  public int m_limit = TERM_LIMIT;

  @Nonnull
  public String getDisplayName() {
    return "Overly complex boolean expression";
  }

  @Nonnull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  private int getLimit() {
    return m_limit;
  }

  public JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel("Maximum number of terms:",
        this, "m_limit");
  }

  protected String buildErrorString(Object... args) {
    return "Overly complex boolean expression #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {

    public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      checkExpression(expression);
    }

    public void visitUnaryExpression(@Nonnull GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(GrExpression expression) {
      if (!isBoolean(expression)) {
        return;
      }
      if (isParentBoolean(expression)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= getLimit()) {
        return;
      }
      registerError(expression);
    }

    private int countTerms(GrExpression expression) {
      if (expression == null) {
        return 0;
      }
      if (!isBoolean(expression)) {
        return 1;
      }
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression = (GrUnaryExpression) expression;
        final GrExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentBoolean(GrExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof GrExpression)) {
        return false;
      }
      return isBoolean((GrExpression) parent);
    }

    private boolean isBoolean(GrExpression expression) {
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final IElementType sign = binaryExpression.getOperationTokenType();
        return GroovyTokenTypes.mLAND.equals(sign) ||
            GroovyTokenTypes.mLOR.equals(sign);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression = (GrUnaryExpression) expression;
        final IElementType sign = prefixExpression.getOperationTokenType();
        return GroovyTokenTypes.mLNOT.equals(sign);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isBoolean(contents);
      }
      return false;
    }
  }
}