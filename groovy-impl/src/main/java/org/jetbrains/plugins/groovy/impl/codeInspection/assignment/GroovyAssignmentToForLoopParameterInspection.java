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
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class GroovyAssignmentToForLoopParameterInspection extends BaseInspection {

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
    return "Assignment to for-loop parameter";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return "Assignment to for-loop parameter '#ref' #loc";

  }

  @Nonnull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    
    @Override
    public void visitAssignmentExpression(GrAssignmentExpression grAssignmentExpression) {
      super.visitAssignmentExpression(grAssignmentExpression);
      final GrExpression lhs = grAssignmentExpression.getLValue();
      if (!(lhs instanceof GrReferenceExpression)) {
        return;
      }
      final PsiElement referent = ((PsiReference) lhs).resolve();
      if (referent == null) {
        return;
      }
      if (!(referent instanceof GrParameter)) {
        return;
      }
      final PsiElement parent = referent.getParent();
      if (!(parent instanceof GrForClause)) {
        return;
      }
      if (parent instanceof GrTraditionalForClause && PsiTreeUtil.isAncestor(parent, grAssignmentExpression, true)) {
        return;
      }
      registerError(lhs);
    }
  }
}
