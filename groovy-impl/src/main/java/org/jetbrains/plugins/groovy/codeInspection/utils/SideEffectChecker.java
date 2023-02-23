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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import javax.annotation.Nonnull;

import consulo.language.ast.IElementType;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class SideEffectChecker {

  private SideEffectChecker() {
    super();
  }

  public static boolean mayHaveSideEffects(@Nonnull GrExpression exp) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor();
    exp.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  private static class SideEffectsVisitor extends GroovyRecursiveElementVisitor {

    private boolean mayHaveSideEffects = false;

    public void visitElement(@Nonnull GroovyPsiElement element) {
      if (!mayHaveSideEffects) {
        super.visitElement(element);
      }
    }

    public void visitAssignmentExpression(
        @Nonnull GrAssignmentExpression expression) {
      if (mayHaveSideEffects) {
        return;
      }
      super.visitAssignmentExpression(expression);
      mayHaveSideEffects = true;
    }

    public void visitMethodCallExpression(
        @Nonnull GrMethodCallExpression expression) {
      if (mayHaveSideEffects) {
        return;
      }
      super.visitMethodCallExpression(expression);
      mayHaveSideEffects = true;
    }

    public void visitNewExpression(@Nonnull GrNewExpression expression) {
      if (mayHaveSideEffects) {
        return;
      }
      super.visitNewExpression(expression);
      mayHaveSideEffects = true;
    }

    public void visitUnaryExpression(
        @Nonnull GrUnaryExpression expression) {
      if (mayHaveSideEffects) {
        return;
      }
      super.visitUnaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(GroovyTokenTypes.mINC) ||
          tokenType.equals(GroovyTokenTypes.mDEC)) {
        mayHaveSideEffects = true;
      }
    }

    public boolean mayHaveSideEffects() {
      return mayHaveSideEffects;
    }
  }
}
