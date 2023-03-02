/*
 * Copyright 2007-2008 Dave Griffith, Bas Leijdekkers
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

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConstantIfStatementInspection extends BaseInspection {

  @Nonnull
  public String getGroupDisplayName() {
    return CONTROL_FLOW;
  }

  @Nonnull
  public String getDisplayName() {
    return "Constant if statement";
  }

  public boolean isEnabledByDefault() {
    return true;
  }

  @Nonnull
  protected String buildErrorString(Object... args) {
    return "#ref statement can be simplified #loc";
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ConstantIfStatementVisitor();
  }

  public GroovyFix buildFix(PsiElement location) {
    return new ConstantIfStatementFix();
  }

  private static class ConstantIfStatementFix extends GroovyFix {

    @Nonnull
    public String getName() {
      return "Simplify";
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement ifKeyword = descriptor.getPsiElement();
      final GrIfStatement ifStatement = (GrIfStatement) ifKeyword.getParent();
      assert ifStatement != null;
      final GrStatement thenBranch = ifStatement.getThenBranch();
      final GrStatement elseBranch = ifStatement.getElseBranch();
      final GrExpression condition = ifStatement.getCondition();
      // todo still needs some handling for conflicting declarations
      if (isFalse(condition)) {
        if (elseBranch != null) {
          replaceStatement(ifStatement, (GrStatement)elseBranch.copy());
        } else {
          ifStatement.delete();
        }
      } else {
        replaceStatement(ifStatement, (GrStatement)thenBranch.copy());
      }
    }
  }

  private static class ConstantIfStatementVisitor
      extends BaseInspectionVisitor {

    public void visitIfStatement(GrIfStatement statement) {
      super.visitIfStatement(statement);
      final GrExpression condition = statement.getCondition();
      if (condition == null) {
        return;
      }
      final GrStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      if (isTrue(condition) || isFalse(condition)) {
        registerStatementError(statement);
      }
    }
  }

  private static boolean isFalse(GrExpression expression) {
    @NonNls final String text = expression.getText();
    return "false".equals(text);
  }

  private static boolean isTrue(GrExpression expression) {
    @NonNls final String text = expression.getText();
    return "true".equals(text);
  }
}