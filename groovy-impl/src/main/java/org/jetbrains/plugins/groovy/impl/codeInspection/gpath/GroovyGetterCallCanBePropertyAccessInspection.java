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
package org.jetbrains.plugins.groovy.impl.codeInspection.gpath;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyGetterCallCanBePropertyAccessInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return GPATH;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Getter call can be property access";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "Call to '#ref' can be property access #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  public GroovyFix buildFix(PsiElement location) {
    return new ReplaceWithPropertyAccessFix();
  }

  private static class ReplaceWithPropertyAccessFix extends GroovyFix {

    @Nonnull
    public String getName() {
      return "Replace with property access";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement referenceName = descriptor.getPsiElement();
      final String getterName = referenceName.getText();
      final String propertyName = Character.toLowerCase(getterName.charAt(3)) + getterName.substring(4);
      final GrReferenceExpression invokedExpression = (GrReferenceExpression) referenceName.getParent();
      final GrMethodCallExpression callExpression = (GrMethodCallExpression) invokedExpression.getParent();
      replaceExpression(callExpression, invokedExpression.getQualifierExpression().getText() + '.' + propertyName);
    }
  }

  private static class Visitor extends BaseInspectionVisitor {
    @NonNls private static final String GET_PREFIX = "get";

    public void visitMethodCallExpression(GrMethodCallExpression grMethodCallExpression) {
      super.visitMethodCallExpression(grMethodCallExpression);
      final GrArgumentList args = grMethodCallExpression.getArgumentList();
      if (args == null) {
        return;
      }
      if (PsiImplUtil.hasExpressionArguments(args)) {
        return;
      }
      if (PsiImplUtil.hasNamedArguments(args)) {
        return;
      }
      final GrExpression methodExpression = grMethodCallExpression.getInvokedExpression();
      if (!(methodExpression instanceof GrReferenceExpression)) {
        return;
      }
      final GrReferenceExpression referenceExpression = (GrReferenceExpression)methodExpression;
      final String name = referenceExpression.getReferenceName();
      if (name == null || !name.startsWith(GET_PREFIX)) {
        return;
      }
      if (name.equals(GET_PREFIX)) {
        return;
      }
      String tail = StringUtil.trimStart(name, GET_PREFIX);
      // If doesn't conform to getter's convention
      if (!tail.equals(StringUtil.capitalize(tail))) {
        return;
      }
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null) return;
      if (PsiUtil.isThisOrSuperRef(qualifier)) return;
      registerMethodCallError(grMethodCallExpression);
    }
  }
}