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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyResultOfIncrementOrDecrementUsedInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Result of increment or decrement used";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Result of increment or decrement expression used #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitUnaryExpression(GrUnaryExpression grUnaryExpression) {
      super.visitUnaryExpression(grUnaryExpression);

      final IElementType tokenType = grUnaryExpression.getOperationTokenType();
      if (!GroovyTokenTypes.mINC.equals(tokenType) && !GroovyTokenTypes.mDEC.equals(tokenType)) {
        return;
      }

      if (PsiUtil.isExpressionUsed(grUnaryExpression)) {
        registerError(grUnaryExpression);
      }
    }
  }
}