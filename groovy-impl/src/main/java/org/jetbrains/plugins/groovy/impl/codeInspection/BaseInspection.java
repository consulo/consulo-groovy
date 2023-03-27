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

import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public abstract class BaseInspection<State> extends GroovySuppressableInspectionTool {
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
  public InspectionToolState<State> createStateProvider() {
    return (InspectionToolState<State>)super.createStateProvider();
  }

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
  protected BaseInspectionVisitor<State> buildGroovyVisitor(@Nonnull ProblemsHolder problemsHolder, boolean onTheFly, State state) {
    final BaseInspectionVisitor<State> visitor = buildVisitor();
    visitor.setProblemsHolder(problemsHolder);
    visitor.setOnTheFly(onTheFly);
    visitor.setInspection(this);
    visitor.setState(state);
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

  @Nonnull
  @Override
  public final PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder, boolean isOnTheFly) {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public final PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        @Nonnull Object state) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        addDescriptors(checkFile(file, holder.getManager(), isOnTheFly, state));
      }

      private void addDescriptors(final ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            holder.registerProblem(Objects.requireNonNull(descriptor));
          }
        }
      }
    };
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public ProblemDescriptor[] checkFile(@Nonnull PsiFile psiFile, @Nonnull InspectionManager inspectionManager, boolean isOnTheFly, Object state) {
    if (!(psiFile instanceof GroovyFileBase)) {
      return super.checkFile(psiFile, inspectionManager, isOnTheFly);
    }
    final GroovyFileBase groovyFile = (GroovyFileBase)psiFile;

    State inspectionState = (State)state;
    final ProblemsHolder problemsHolder = new ProblemsHolder(inspectionManager, psiFile, isOnTheFly);
    final BaseInspectionVisitor visitor = buildGroovyVisitor(problemsHolder, isOnTheFly, inspectionState);
    groovyFile.accept(visitor);
    return problemsHolder.getResultsArray();

  }

  @Nonnull
  protected abstract BaseInspectionVisitor<State> buildVisitor();
}
