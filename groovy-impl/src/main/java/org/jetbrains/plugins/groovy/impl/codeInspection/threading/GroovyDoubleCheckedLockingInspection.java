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

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

public class GroovyDoubleCheckedLockingInspection extends BaseInspection<GroovyDoubleCheckedLockingInspectionState> {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return THREADING_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Double-checked locking");
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return "Double-checked locking #loc";
    }

    @Nonnull
    @Override
    public InspectionToolState<GroovyDoubleCheckedLockingInspectionState> createStateProvider() {
        return new GroovyDoubleCheckedLockingInspectionState();
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor<GroovyDoubleCheckedLockingInspectionState> buildVisitor() {
        return new DoubleCheckedLockingVisitor();
    }

    private class DoubleCheckedLockingVisitor extends BaseInspectionVisitor<GroovyDoubleCheckedLockingInspectionState> {
        @Override
        @RequiredReadAction
        public void visitIfStatement(@Nonnull GrIfStatement statement) {
            super.visitIfStatement(statement);
            GrExpression outerCondition = statement.getCondition();
            if (outerCondition == null) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(outerCondition)) {
                return;
            }
            GrStatement thenBranch = statement.getThenBranch();
            if (thenBranch == null) {
                return;
            }
            thenBranch = ControlFlowUtils.stripBraces(thenBranch);
            if (!(thenBranch instanceof GrSynchronizedStatement syncStmt)) {
                return;
            }
            GrCodeBlock body = syncStmt.getBody();
            if (body == null) {
                return;
            }
            GrStatement[] statements = body.getStatements();
            if (statements.length != 1) {
                return;
            }
            if (!(statements[0] instanceof GrIfStatement innerIf)) {
                return;
            }
            GrExpression innerCondition = innerIf.getCondition();
            if (innerCondition == null) {
                return;
            }
            if (!EquivalenceChecker.expressionsAreEquivalent(innerCondition, outerCondition)) {
                return;
            }
            if (myState.ignoreOnVolatileVariables && ifStatementAssignsVolatileVariable(innerIf)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean ifStatementAssignsVolatileVariable(GrIfStatement statement) {
            return ControlFlowUtils.stripBraces(statement.getThenBranch()) instanceof GrAssignmentExpression assignment
                && assignment.getLValue() instanceof GrReferenceExpression lhsRef
                && lhsRef.resolve() instanceof PsiField field
                && field.hasModifierProperty(PsiModifier.VOLATILE);
        }
    }
}
