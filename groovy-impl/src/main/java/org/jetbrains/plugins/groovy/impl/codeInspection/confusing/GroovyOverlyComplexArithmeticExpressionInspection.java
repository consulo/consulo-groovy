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

import com.intellij.java.language.psi.PsiType;
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
import java.util.HashSet;
import java.util.Set;

public class GroovyOverlyComplexArithmeticExpressionInspection extends BaseInspection {

  private static final int TERM_LIMIT = 3;

  /**
   * @noinspection PublicField,WeakerAccess
   */
  public int m_limit = TERM_LIMIT;

  @Nonnull
  public String getDisplayName() {
    return "Overly complex arithmetic expression";
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
    return "Overly complex arithmetic expression #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    private final Set<IElementType> arithmeticTokens = new HashSet<IElementType>(5);

    {
      arithmeticTokens.add(GroovyTokenTypes.mPLUS);
      arithmeticTokens.add(GroovyTokenTypes.mMINUS);
      arithmeticTokens.add(GroovyTokenTypes.mSTAR);
      arithmeticTokens.add(GroovyTokenTypes.mDIV);
      arithmeticTokens.add(GroovyTokenTypes.mMOD);
    }

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
      if (isParentArithmetic(expression)) {
        return;
      }
      if (!isArithmetic(expression)) {
        return;
      }
      if (containsStringConcatenation(expression)) {
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
      if (!isArithmetic(expression)) {
        return 1;
      }
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final GrExpression operand = unaryExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentArithmetic(GrExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof GrExpression)) {
        return false;
      }
      return isArithmetic((GrExpression) parent);
    }

    private boolean isArithmetic(GrExpression expression) {
      if (expression instanceof GrBinaryExpression) {

        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final IElementType sign = binaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final IElementType sign = unaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isArithmetic(contents);
      }
      return false;
    }

    private boolean containsStringConcatenation(GrExpression expression) {
      if (isString(expression)) {
        return true;
      }
      if (expression instanceof GrBinaryExpression) {

        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();

        if (containsStringConcatenation(lhs)) {
          return true;
        }
        final GrExpression rhs = binaryExpression.getRightOperand();
        return containsStringConcatenation(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final GrExpression operand = unaryExpression.getOperand();
        return containsStringConcatenation(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return containsStringConcatenation(contents);
      }
      return false;
    }

    private boolean isString(GrExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      return "java.lang.String".equals(type.getCanonicalText());
    }
  }
}