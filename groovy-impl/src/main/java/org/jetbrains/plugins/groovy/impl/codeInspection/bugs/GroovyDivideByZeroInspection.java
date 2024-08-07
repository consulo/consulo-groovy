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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import jakarta.annotation.Nullable;

import consulo.language.ast.IElementType;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyDivideByZeroInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Divide by zero";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Division by zero #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitBinaryExpression(
      @Nonnull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!GroovyTokenTypes.mDIV.equals(tokenType) &&
          !GroovyTokenTypes.mMOD.equals(tokenType)) {
        return;
      }
      if (!isZero(rhs)) {
        return;
      }
      registerError(expression);
    }

    public void visitAssignmentExpression(
      GrAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final GrExpression rhs = expression.getRValue();
      if (rhs == null) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(GroovyTokenTypes.mDIV_ASSIGN)
          && !tokenType.equals(GroovyTokenTypes.mMOD_ASSIGN)) {
        return;
      }
      if (!isZero(rhs)) {
        return;
      }
      registerError(expression);
    }


  }

  private static boolean isZero(GrExpression expression) {
    @NonNls
    final String text = expression.getText();
    return "0".equals(text) ||
           "0x0".equals(text) ||
           "0X0".equals(text) ||
           "0.0".equals(text) ||
           "0L".equals(text) ||
           "0l".equals(text);
  }
}