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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyDoubleNegationInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nonnull
  public String getDisplayName() {
    return "Double negation";
  }

  @Nonnull
  protected String buildErrorString(Object... infos) {
    return "Double negation #ref #loc";
  }

  @Nullable
  protected GroovyFix buildFix(PsiElement location) {
    return new DoubleNegationFix();
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  private static class DoubleNegationFix extends GroovyFix {

    @Nonnull
    public String getName() {
      return "Remove double negation";
    }

    protected void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final GrUnaryExpression expression =
          (GrUnaryExpression) descriptor.getPsiElement();
      GrExpression operand = (GrExpression)PsiUtil.skipParentheses(expression.getOperand(), false);
      if (operand instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression =
            (GrUnaryExpression) operand;
        final GrExpression innerOperand = prefixExpression.getOperand();
        if (innerOperand == null) {
          return;
        }
        replaceExpression(expression, innerOperand.getText());
      } else if (operand instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression =
            (GrBinaryExpression) operand;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final String lhsText = lhs.getText();
        final StringBuilder builder =
            new StringBuilder(lhsText);
        builder.append("==");
        final GrExpression rhs = binaryExpression.getRightOperand();
        if (rhs != null) {
          final String rhsText = rhs.getText();
          builder.append(rhsText);
        }
        replaceExpression(expression, builder.toString());
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new DoubleNegationVisitor();
  }

  private static class DoubleNegationVisitor extends BaseInspectionVisitor {

    public void visitUnaryExpression(GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mLNOT.equals(tokenType)) {
        return;
      }
      checkParent(expression);
    }

    public void visitBinaryExpression(GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mNOT_EQUAL.equals(tokenType)) {
        return;
      }
      checkParent(expression);
    }

    private void checkParent(GrExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof GrParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof GrUnaryExpression)) {
        return;
      }
      final GrUnaryExpression prefixExpression =
          (GrUnaryExpression) parent;
      final IElementType parentTokenType =
          prefixExpression.getOperationTokenType();
      if (!GroovyTokenTypes.mLNOT.equals(parentTokenType)) {
        return;
      }
      registerError(prefixExpression);
    }
  }
}