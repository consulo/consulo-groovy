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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

@ExtensionImpl
public class GroovySillyAssignmentInspection extends BaseInspection {

  @Override
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return ASSIGNMENT_ISSUES;
  }

  @Override
  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Silly assignment";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Silly assignment #loc";

  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nonnull
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@Nonnull GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);

      final IElementType sign = assignment.getOperationTokenType();
      if (!sign.equals(GroovyTokenTypes.mASSIGN)) {
        return;
      }
      final GrExpression lhs = assignment.getLValue();
      final GrExpression rhs = assignment.getRValue();
      if (rhs == null) {
        return;
      }
      if (!(rhs instanceof GrReferenceExpression) || !(lhs instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression rhsReference = (GrReferenceExpression) rhs;
      final GrReferenceExpression lhsReference = (GrReferenceExpression) lhs;
      final GrExpression rhsQualifier = rhsReference.getQualifierExpression();
      final GrExpression lhsQualifier = lhsReference.getQualifierExpression();
      if (rhsQualifier != null || lhsQualifier != null) {
        if (!EquivalenceChecker.expressionsAreEquivalent(rhsQualifier, lhsQualifier)) {
          return;
        }
      }
      final String rhsName = rhsReference.getReferenceName();
      final String lhsName = lhsReference.getReferenceName();
      if (rhsName == null || lhsName == null) {
        return;
      }
      if (!rhsName.equals(lhsName)) {
        return;
      }
      final PsiElement rhsReferent = rhsReference.resolve();
      final PsiElement lhsReferent = lhsReference.resolve();
      if (rhsReferent == null || lhsReferent == null || !rhsReferent.equals(lhsReferent)) {
        return;
      }
      registerError(assignment);
    }
  }
}
