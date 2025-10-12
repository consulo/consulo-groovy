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
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.inspection.localize.InspectionLocalize;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

public abstract class BaseInspection<State> extends GroovySuppressableInspectionTool {
    public static final LocalizeValue ASSIGNMENT_ISSUES = LocalizeValue.localizeTODO("Assignment issues");
    public static final LocalizeValue CONFUSING_CODE_CONSTRUCTS = LocalizeValue.localizeTODO("Potentially confusing code constructs");
    public static final LocalizeValue CONTROL_FLOW = LocalizeValue.localizeTODO("Control Flow");
    public static final LocalizeValue PROBABLE_BUGS = LocalizeValue.localizeTODO("Probable bugs");
    public static final LocalizeValue ERROR_HANDLING = LocalizeValue.localizeTODO("Error handling");
    public static final LocalizeValue GPATH = LocalizeValue.localizeTODO("GPath inspections");
    public static final LocalizeValue METHOD_METRICS = LocalizeValue.localizeTODO("Method Metrics");
    public static final LocalizeValue THREADING_ISSUES = LocalizeValue.localizeTODO("Threading issues");
    public static final LocalizeValue VALIDITY_ISSUES = LocalizeValue.localizeTODO("Validity issues");
    public static final LocalizeValue ANNOTATIONS_ISSUES = LocalizeValue.localizeTODO("Annotations verifying");

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public InspectionToolState<State> createStateProvider() {
        return (InspectionToolState<State>) super.createStateProvider();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return InspectionLocalize.inspectionGeneralToolsGroupName();
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
    @SuppressWarnings("unchecked")
    public final PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        @Nonnull Object state
    ) {
        PsiFile file = holder.getFile();
        if (!(file instanceof GroovyFileBase)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }

        State inspectionState = (State) state;
        return buildGroovyVisitor(holder, isOnTheFly, inspectionState);
    }

    @Nonnull
    protected abstract BaseInspectionVisitor<State> buildVisitor();
}
