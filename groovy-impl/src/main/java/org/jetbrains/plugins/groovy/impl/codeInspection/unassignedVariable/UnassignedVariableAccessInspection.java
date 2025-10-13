/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.unassignedVariable;

import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.GrInspectionUtil;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
@ExtensionImpl
public class UnassignedVariableAccessInspection extends GroovyLocalInspectionBase<UnassignedVariableAccessInspectionState> {
    @Nonnull
    @Override
    public InspectionToolState<?> createStateProvider() {
        return new UnassignedVariableAccessInspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return GroovyInspectionLocalize.groovyDfaIssues();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.unassignedAccess();
    }

    @Nonnull
    @Override
    public String getShortName() {
        return "GroovyVariableNotAssigned";
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    protected void check(GrControlFlowOwner owner, ProblemsHolder problemsHolder, UnassignedVariableAccessInspectionState state) {
        Instruction[] flow = owner.getControlFlow();
        ReadWriteVariableInstruction[] reads = ControlFlowBuilderUtil.getReadsWithoutPriorWrites(flow, true);
        for (ReadWriteVariableInstruction read : reads) {
            PsiElement element = read.getElement();
            if (element instanceof GroovyPsiElement) {
                String name = read.getVariableName();
                GroovyPsiElement property = ResolveUtil.resolveProperty((GroovyPsiElement) element, name);
                if (property != null &&
                    !(property instanceof PsiParameter) &&
                    !(property instanceof PsiField) &&
                    PsiTreeUtil.isAncestor(owner, property, false) &&
                    !(state.myIgnoreBooleanExpressions && isBooleanCheck(element))
                ) {
                    problemsHolder.registerProblem(element, GroovyInspectionLocalize.unassignedAccessTooltip(name).get());
                }
            }
        }
    }

    private static boolean isBooleanCheck(PsiElement element) {
        final PsiElement parent = element.getParent();
        return parent instanceof GrIfStatement && ((GrIfStatement) parent).getCondition() == element ||
            parent instanceof GrWhileStatement && ((GrWhileStatement) parent).getCondition() == element ||
            parent instanceof GrTraditionalForClause && ((GrTraditionalForClause) parent).getCondition() == element ||
            isLogicalExpression(parent) ||
            parent instanceof GrUnaryExpression && ((GrUnaryExpression) parent).getOperationTokenType() == GroovyTokenTypes.mBNOT ||
            isCheckForNull(parent, element);
    }

    private static boolean isLogicalExpression(PsiElement parent) {
        return parent instanceof GrBinaryExpression &&
            (((GrBinaryExpression) parent).getOperationTokenType() == GroovyTokenTypes.mLAND ||
                ((GrBinaryExpression) parent).getOperationTokenType() == GroovyTokenTypes.mLOR);
    }

    private static boolean isCheckForNull(PsiElement parent, PsiElement element) {
        if (!(parent instanceof GrBinaryExpression)) {
            return false;
        }

        final IElementType tokenType = ((GrBinaryExpression) parent).getOperationTokenType();
        if (!(tokenType == GroovyTokenTypes.mEQUAL || tokenType == GroovyTokenTypes.mNOT_EQUAL)) {
            return false;
        }
        if (element == ((GrBinaryExpression) parent).getLeftOperand()) {
            final GrExpression rightOperand = ((GrBinaryExpression) parent).getRightOperand();
            return rightOperand != null && GrInspectionUtil.isNull(rightOperand);
        }
        else {
            return GrInspectionUtil.isNull(((GrBinaryExpression) parent).getLeftOperand());
        }
    }
}
