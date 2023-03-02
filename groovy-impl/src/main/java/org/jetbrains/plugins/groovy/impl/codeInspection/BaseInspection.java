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

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class BaseInspection extends GroovySuppressableInspectionTool {

  private final String m_shortName = StringUtil.trimEnd(getClass().getSimpleName(), "Inspection");

  public static final String ASSIGNMENT_ISSUES = "Assignment issues";
  public static final String CONFUSING_CODE_CONSTRUCTS = "Potentially confusing code constructs";
  public static final String CONTROL_FLOW = "Control Flow";
  public static final String PROBABLE_BUGS = "Probable bugs";
  public static final String ERROR_HANDLING = "Error handling";
  public static final String GPATH = "GPath inspections";
  public static final String METHOD_METRICS = "Method Metrics";
  public static final String THREADING_ISSUES = "Threading issues";
  public static final String VALIDITY_ISSUES = "Validity issues";
  public static final String ANNOTATIONS_ISSUES = "Annotations verifying";

  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return "General";
  }

  @Nonnull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @Nonnull
  public String getShortName() {
    return m_shortName;
  }

  @Nonnull
  protected BaseInspectionVisitor buildGroovyVisitor(@Nonnull ProblemsHolder problemsHolder, boolean onTheFly) {
    final BaseInspectionVisitor visitor = buildVisitor();
    visitor.setProblemsHolder(problemsHolder);
    visitor.setOnTheFly(onTheFly);
    visitor.setInspection(this);
    return visitor;
  }


  @Nullable
  protected String buildErrorString(Object... args) {
    return null;
  }

  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return false;
  }

  @Nullable
  protected GroovyFix buildFix(@Nonnull PsiElement location) {
    return null;
  }

  @Nullable
  protected GroovyFix[] buildFixes(@Nonnull PsiElement location) {
    return null;
  }

  @Nullable
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile psiFile, @Nonnull InspectionManager inspectionManager, boolean isOnTheFly) {
    if (!(psiFile instanceof GroovyFileBase)) {
      return super.checkFile(psiFile, inspectionManager, isOnTheFly);
    }
    final GroovyFileBase groovyFile = (GroovyFileBase)psiFile;

    final ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, psiFile, isOnTheFly);
    final BaseInspectionVisitor visitor = buildGroovyVisitor(problemsHolder, isOnTheFly);
    groovyFile.accept(visitor);
    return problemsHolder.getResultsArray();

  }

  @Nonnull
  protected abstract BaseInspectionVisitor buildVisitor();
}
