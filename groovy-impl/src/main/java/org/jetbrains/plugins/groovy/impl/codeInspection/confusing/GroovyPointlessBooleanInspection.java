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
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class GroovyPointlessBooleanInspection extends BaseInspection {

  @Nonnull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("pointless.boolean.display.name");
  }

  @Nonnull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  public String buildErrorString(Object... args) {
    if (args[0] instanceof GrBinaryExpression) {
      return GroovyInspectionBundle.message(
          "pointless.boolean.problem.descriptor",
          calculateSimplifiedBinaryExpression((GrBinaryExpression) args[0]));
    } else {
      return GroovyInspectionBundle.message(
          "pointless.boolean.problem.descriptor",
          calculateSimplifiedPrefixExpression((GrUnaryExpression) args[0]));
    }
  }

  @Nullable
  private static String calculateSimplifiedBinaryExpression(GrBinaryExpression expression) {
    final IElementType sign = expression.getOperationTokenType();
    final GrExpression lhs = expression.getLeftOperand();

    final GrExpression rhs = expression.getRightOperand();
    if (rhs == null) {
      return null;
    }
    final String rhsText = rhs.getText();
    final String lhsText = lhs.getText();
    assert sign != null;
    if (sign.equals(GroovyTokenTypes.mLAND)) {
      if (isTrue(lhs)) {
        return rhsText;
      } else {
        return lhsText;
      }
    } else if (sign.equals(GroovyTokenTypes.mLOR)) {
      if (isFalse(lhs)) {
        return rhsText;
      } else {
        return lhsText;
      }
    } else if (sign.equals(GroovyTokenTypes.mBXOR) ||
        sign.equals(GroovyTokenTypes.mNOT_EQUAL)) {
      if (isFalse(lhs)) {
        return rhsText;
      } else if (isFalse(rhs)) {
        return lhsText;
      } else if (isTrue(lhs)) {
        return createStringForNegatedExpression(rhs);
      } else {
        return createStringForNegatedExpression(lhs);
      }
    } else if (sign.equals(GroovyTokenTypes.mEQUAL)) {
      if (isTrue(lhs)) {
        return rhsText;
      } else if (isTrue(rhs)) {
        return lhsText;
      } else if (isFalse(lhs)) {
        return createStringForNegatedExpression(rhs);
      } else {
        return createStringForNegatedExpression(lhs);
      }
    } else {
      return "";
    }
  }

  private static String createStringForNegatedExpression(GrExpression exp) {
    if (ComparisonUtils.isComparison(exp)) {
      final GrBinaryExpression binaryExpression =
          (GrBinaryExpression) exp;
      final IElementType sign = binaryExpression.getOperationTokenType();
      final String negatedComparison =
          ComparisonUtils.getNegatedComparison(sign);
      final GrExpression lhs = binaryExpression.getLeftOperand();
      final GrExpression rhs = binaryExpression.getRightOperand();
      if (rhs == null) {
        return lhs.getText() + negatedComparison;
      }
      return lhs.getText() + negatedComparison + rhs.getText();
    } else {
      final String baseText = exp.getText();
      if (ParenthesesUtils.getPrecedence(exp) >
          ParenthesesUtils.PREFIX_PRECEDENCE) {
        return "!(" + baseText + ')';
      } else {
        return '!' + baseText;
      }
    }
  }

  @NonNls
  private static String calculateSimplifiedPrefixExpression(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    if (isTrue(operand)) {
      return "false";
    } else {
      return "true";
    }
  }

  public GroovyFix buildFix(PsiElement location) {
    return new BooleanLiteralComparisonFix();
  }

  private static class BooleanLiteralComparisonFix
      extends GroovyFix {

    @Nonnull
    public String getName() {
      return GroovyInspectionBundle.message("pointless.boolean.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException
	{
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof GrBinaryExpression) {
        final GrBinaryExpression expression =
            (GrBinaryExpression) element;
        final String replacementString =
            calculateSimplifiedBinaryExpression(expression);
        replaceExpression(expression, replacementString);
      } else {
        final GrUnaryExpression expression =
            (GrUnaryExpression) element;
        final String replacementString =
            calculateSimplifiedPrefixExpression(expression);
        replaceExpression(expression, replacementString);
      }
    }
  }

  private static class PointlessBooleanExpressionVisitor
      extends BaseInspectionVisitor {

    private final Set<IElementType> booleanTokens =
        new HashSet<IElementType>(5);

    {
      booleanTokens.add(GroovyTokenTypes.mLAND);
      booleanTokens.add(GroovyTokenTypes.mLOR);
      booleanTokens.add(GroovyTokenTypes.mBXOR);
      booleanTokens.add(GroovyTokenTypes.mEQUAL);
      booleanTokens.add(GroovyTokenTypes.mNOT_EQUAL);
    }

    public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }
      final IElementType sign = expression.getOperationTokenType();
      if (!booleanTokens.contains(sign)) {
        return;
      }


      final GrExpression lhs = expression.getLeftOperand();

      assert sign != null;
      final boolean isPointless;
      if (sign.equals(GroovyTokenTypes.mEQUAL) ||
          sign.equals(GroovyTokenTypes.mNOT_EQUAL)) {
        isPointless = equalityExpressionIsPointless(lhs, rhs);
      } else if (sign.equals(GroovyTokenTypes.mLAND)) {
        isPointless = andExpressionIsPointless(lhs, rhs);
      } else if (sign.equals(GroovyTokenTypes.mLOR)) {
        isPointless = orExpressionIsPointless(lhs, rhs);
      } else if (sign.equals(GroovyTokenTypes.mBXOR)) {
        isPointless = xorExpressionIsPointless(lhs, rhs);
      } else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }
      registerError(expression);
    }

    public void visitUnaryExpression(@Nonnull GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final IElementType sign = expression.getOperationTokenType();
      if (sign == null) {
        return;
      }
      final GrExpression operand = expression.getOperand();
      if (sign.equals(GroovyTokenTypes.mLNOT) &&
          notExpressionIsPointless(operand)) {
        registerError(expression);
      }
    }
  }

  private static boolean equalityExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return (isTrue(lhs) || isFalse(lhs)) && isBoolean(rhs)
           || (isTrue(rhs) || isFalse(rhs)) && isBoolean(lhs);
  }

  private static boolean isBoolean(GrExpression expression) {
    final PsiType type = expression.getType();
    final PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
    return unboxed != null && PsiType.BOOLEAN.equals(unboxed);
  }

  private static boolean andExpressionIsPointless(GrExpression lhs,
                                                  GrExpression rhs) {
    return isTrue(lhs) || isTrue(rhs);
  }

  private static boolean orExpressionIsPointless(GrExpression lhs,
                                                 GrExpression rhs) {
    return isFalse(lhs) || isFalse(rhs);
  }

  private static boolean xorExpressionIsPointless(GrExpression lhs,
                                                  GrExpression rhs) {
    return isTrue(lhs) || isTrue(rhs) || isFalse(lhs) || isFalse(rhs);
  }

  private static boolean notExpressionIsPointless(GrExpression arg) {
    return isFalse(arg) || isTrue(arg);
  }

  private static boolean isTrue(GrExpression expression) {
    if (expression == null) {
      return false;
    }
    if (!(expression instanceof GrLiteral)) {
      return false;
    }
    @NonNls final String text = expression.getText();
    return "true".equals(text);
  }

  private static boolean isFalse(GrExpression expression) {
    if (expression == null) {
      return false;
    }
    if (!(expression instanceof GrLiteral)) {
      return false;
    }
    @NonNls final String text = expression.getText();
    return "false".equals(text);
  }
}
