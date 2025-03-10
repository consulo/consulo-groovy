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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public abstract class BaseInspectionVisitor<State> extends GroovyElementVisitor {
  private BaseInspection inspection = null;
  private ProblemsHolder problemsHolder = null;
  private boolean onTheFly = false;

  protected State myState;

  public void setInspection(BaseInspection inspection) {
    this.inspection = inspection;
  }

  public void setProblemsHolder(ProblemsHolder problemsHolder) {
    this.problemsHolder = problemsHolder;
  }

  public void setOnTheFly(boolean onTheFly) {
    this.onTheFly = onTheFly;
  }

  public void setState(State state) {
    myState = state;
  }

  protected void registerStatementError(GrStatement statement, Object... args) {
    final PsiElement statementToken = statement.getFirstChild();
    registerError(statementToken, args);
  }

  protected void registerClassError(GrTypeDefinition aClass, Object... args) {
    final PsiElement statementToken = aClass.getNameIdentifierGroovy();
    registerError(statementToken, args);
  }

  protected void registerError(PsiElement location) {
    if (location == null) {
      return;
    }
    final LocalQuickFix[] fix = createFixes(location);
    String description = StringUtil.notNullize(inspection.buildErrorString(location));

    registerError(location, description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerMethodError(GrMethod method, Object... args) {
    if (method == null) {
      return;
    }
    final LocalQuickFix[] fixes = createFixes(method);
    String description = StringUtil.notNullize(inspection.buildErrorString(args));

    registerError(method.getNameIdentifierGroovy(), description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerVariableError(GrVariable variable, Object... args) {
    if (variable == null) {
      return;
    }
    final LocalQuickFix[] fix = createFixes(variable);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));
    registerError(variable.getNameIdentifierGroovy(), description, fix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerMethodCallError(GrMethodCallExpression method, Object... args) {
    if (method == null) {
      return;
    }
    final LocalQuickFix[] fixes = createFixes(method);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));

    final GrExpression invoked = method.getInvokedExpression();
    assert invoked != null;
    final PsiElement nameElement = ((GrReferenceExpression)invoked).getReferenceNameElement();
    assert nameElement != null;
    registerError(nameElement, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  protected void registerError(@Nonnull PsiElement location,
                               @Nonnull String description,
                               @Nullable LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType) {
    problemsHolder.registerProblem(location, description, highlightType, fixes);
  }

  protected void registerError(@Nonnull PsiElement location, Object... args) {
    registerError(location, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, args);
  }

  protected void registerError(@Nonnull PsiElement location,
                               ProblemHighlightType highlightType,
                               Object... args) {
    final LocalQuickFix[] fix = createFixes(location);
    final String description = StringUtil.notNullize(inspection.buildErrorString(args));
    registerError(location, description, fix, highlightType);
  }

  @Nullable
  private LocalQuickFix[] createFixes(@Nonnull PsiElement location) {
    if (!onTheFly &&
        inspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
      return null;
    }
    final GroovyFix[] fixes = inspection.buildFixes(location);
    if (fixes != null) {
      return fixes;
    }
    final GroovyFix fix = inspection.buildFix(location);
    if (fix == null) {
      return null;
    }
    return new GroovyFix[]{fix};
  }

  public int getErrorCount() {
    return problemsHolder.getResultCount();
  }
}
