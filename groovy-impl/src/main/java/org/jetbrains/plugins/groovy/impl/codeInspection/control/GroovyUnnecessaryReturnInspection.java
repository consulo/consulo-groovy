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
package org.jetbrains.plugins.groovy.impl.codeInspection.control;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyUnnecessaryReturnInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Unnecessary 'return' statement";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "#ref is unnecessary as the last statement in a method with no return value #loc";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nullable
  protected GroovyFix buildFix(PsiElement location) {
    return new UnnecessaryReturnFix();

  }

  private static class UnnecessaryReturnFix extends GroovyFix {
    @Nonnull
    public String getName() {
      return "Remove unnecessary return";
    }

    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement returnKeywordElement = descriptor.getPsiElement();
      final GrReturnStatement returnStatement = (GrReturnStatement) returnKeywordElement.getParent();
      assert returnStatement != null;
      returnStatement.removeStatement();
    }
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitReturnStatement(GrReturnStatement returnStatement) {
      super.visitReturnStatement(returnStatement);

      final GrExpression returnValue = returnStatement.getReturnValue();
      if (returnValue != null) return;

      final GrMethod method = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class);
      if (method == null) return;

      final GrOpenBlock body = method.getBlock();
      if (body == null) return;

      if (ControlFlowUtils.openBlockCompletesWithStatement(body, returnStatement)) {
        registerStatementError(returnStatement);
      }
    }
  }
}