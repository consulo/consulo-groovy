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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

public class GroovyPointlessArithmeticInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return "Pointless arithmetic expression";
  }

  @Nonnull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  public boolean isEnabledByDefault() {
    return false;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PointlessArithmeticVisitor();
  }

  public String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("pointless.arithmetic.error.message", calculateReplacementExpression((GrExpression) args[0]));
  }

  private static String calculateReplacementExpression(GrExpression expression) {
    final GrBinaryExpression exp = (GrBinaryExpression)expression;
    final IElementType sign = exp.getOperationTokenType();
    final GrExpression lhs = exp.getLeftOperand();
    final GrExpression rhs = exp.getRightOperand();
    assert rhs != null;
    if (mPLUS == sign) {
      if (isZero(lhs)) {
        return rhs.getText();
      }
      else {
        return lhs.getText();
      }
    }

    if (mMINUS == sign) {
      return lhs.getText();
    }

    if (mSTAR == sign) {
      if (isOne(lhs)) {
        return rhs.getText();
      }
      else if (isOne(rhs)) {
        return lhs.getText();
      }
      else {
        return "0";
      }
    }

    if (mDIV == sign) {
      return lhs.getText();
    }

    return "";
  }

  public GroovyFix buildFix(PsiElement location) {
    return new PointlessArithmeticFix();
  }

  private static class PointlessArithmeticFix extends GroovyFix {
    @Nonnull
    public String getName() {
      return "Simplify";
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final GrExpression expression = (GrExpression) descriptor.getPsiElement();
      final String newExpression = calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }
  }

  private static class PointlessArithmeticVisitor extends BaseInspectionVisitor {

    private final TokenSet arithmeticTokens = TokenSet.create(mPLUS, mMINUS, mSTAR, mDIV);

    public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) return;

      final IElementType sign = expression.getOperationTokenType();
      if (!arithmeticTokens.contains(sign)) return;

      final GrExpression lhs = expression.getLeftOperand();

      final boolean isPointless;
      if (sign.equals(mPLUS)) {
        isPointless = additionExpressionIsPointless(lhs, rhs);
      }
      else if (sign.equals(mMINUS)) {
        isPointless = subtractionExpressionIsPointless(rhs);
      }
      else if (sign.equals(mSTAR)) {
        isPointless = multiplyExpressionIsPointless(lhs, rhs);
      }
      else if (sign.equals(mDIV)) {
        isPointless = divideExpressionIsPointless(rhs);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }

      registerError(expression);
    }
  }

  private static boolean subtractionExpressionIsPointless(GrExpression rhs) {
    return isZero(rhs);
  }

  private static boolean additionExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs);
  }

  private static boolean multiplyExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
  }

  private static boolean divideExpressionIsPointless(GrExpression rhs) {
    return isOne(rhs);
  }

  /**
   * @noinspection FloatingPointEquality
   */
  private static boolean isZero(GrExpression expression) {
    final PsiElement inner = PsiUtil.skipParentheses(expression, false);
    if (inner == null) return false;

    @NonNls final String text = inner.getText();
    return "0".equals(text) ||
           "0x0".equals(text) ||
           "0X0".equals(text) ||
           "0.0".equals(text) ||
           "0L".equals(text) ||
           "0l".equals(text) ||
           "0b0".equals(text) ||
           "0B0".equals(text);
  }

  /**
   * @noinspection FloatingPointEquality
   */
  private static boolean isOne(GrExpression expression) {
    final PsiElement inner = PsiUtil.skipParentheses(expression, false);
    if (inner == null) return false;

    @NonNls final String text = inner.getText();
    return "1".equals(text) ||
           "0x1".equals(text) ||
           "0X1".equals(text) ||
           "1.0".equals(text) ||
           "1L".equals(text) ||
           "1l".equals(text) ||
           "0b0".equals(text) ||
           "0B0".equals(text);
  }
}