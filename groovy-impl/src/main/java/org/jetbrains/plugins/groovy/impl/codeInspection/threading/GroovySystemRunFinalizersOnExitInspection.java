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
package org.jetbrains.plugins.groovy.impl.codeInspection.threading;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class GroovySystemRunFinalizersOnExitInspection extends BaseInspection {
  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return THREADING_ISSUES;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Call to System.runFinalizersOnExit()";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to System.'#ref' #loc";

  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private static class Visitor extends BaseInspectionVisitor {
    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression reference = (GrReferenceExpression) methodExpression;
      final String name = reference.getReferenceName();
      if (!"runFinalizersOnExit".equals(name)) {
        return;
      }
      final PsiMethod method = grMethodCallExpression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"java.lang.System".equals(containingClass.getQualifiedName())) {
        return;
      }
      registerMethodCallError(grMethodCallExpression);
    }
  }
}