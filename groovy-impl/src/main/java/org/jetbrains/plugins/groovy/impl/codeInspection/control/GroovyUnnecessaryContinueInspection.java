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

import jakarta.annotation.Nullable;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.language.template.TemplateLanguageFileViewProvider;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;

public class GroovyUnnecessaryContinueInspection extends BaseInspection {

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return "Unnecessary 'continue' statement";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return "#ref is unnecessary as the last statement in a loop #loc";

  }

  public boolean isEnabledByDefault() {
    return true;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nullable
  protected GroovyFix buildFix(PsiElement location) {
    return new UnnecessaryContinueFix();
  }

  private static class UnnecessaryContinueFix extends GroovyFix {

    @Nonnull
    public String getName() {
      return "Remove unnecessary continue";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException
	{
      final PsiElement continueKeywordElement = descriptor.getPsiElement();
      final GrContinueStatement continueStatement = (GrContinueStatement) continueKeywordElement.getParent();
      assert continueStatement != null;
      continueStatement.removeStatement();
    }
  }

  private static class Visitor extends BaseInspectionVisitor {

    public void visitContinueStatement(GrContinueStatement continueStatement) {
      super.visitContinueStatement(continueStatement);
      if (continueStatement.getContainingFile().getViewProvider() instanceof TemplateLanguageFileViewProvider) {
        return;
      }
      final GrStatement continuedStatement = continueStatement.findTargetStatement();
      if (continuedStatement == null) {
        return;
      }

      if (!(continuedStatement instanceof GrLoopStatement)) {
        return;
      }
      final GrCondition body = ((GrLoopStatement) continuedStatement).getBody();
      if (body == null) {
        return;
      }
      if (body instanceof GrBlockStatement) {
        if (ControlFlowUtils.blockCompletesWithStatement((GrBlockStatement) body,
            continueStatement)) {
          registerStatementError(continueStatement);
        }
      } else if (body instanceof GrStatement) {
        if (ControlFlowUtils.statementCompletesWithStatement((GrStatement) body,
            continueStatement)) {
          registerStatementError(continueStatement);
        }
      }
    }
  }
}