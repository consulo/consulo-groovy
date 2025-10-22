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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import com.intellij.java.language.psi.JavaTokenType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Set;

@ExtensionImpl
public class GroovyAssignmentCanBeOperatorAssignmentInspection
    extends BaseInspection<GroovyAssignmentCanBeOperatorAssignmentInspectionState> {
    @Nonnull
    @Override
    public InspectionToolState<GroovyAssignmentCanBeOperatorAssignmentInspectionState> createStateProvider() {
        return new GroovyAssignmentCanBeOperatorAssignmentInspectionState();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return ASSIGNMENT_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Assignment replaceable with operator assignment");
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String buildErrorString(Object... infos) {
        GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) infos[0];
        return "<code>#ref</code> could be simplified to '" + calculateReplacementExpression(assignmentExpression) + "' #loc";
    }

    @RequiredReadAction
    static String calculateReplacementExpression(GrAssignmentExpression expression) {
        GrExpression rhs = expression.getRValue();
        GrBinaryExpression binaryExpression = (GrBinaryExpression) PsiUtil.skipParentheses(rhs, false);
        GrExpression lhs = expression.getLValue();
        assert binaryExpression != null;
        IElementType sign = binaryExpression.getOperationTokenType();
        GrExpression rhsRhs = binaryExpression.getRightOperand();
        assert rhsRhs != null;
        String signText = getTextForOperator(sign);
        if ("&&".equals(signText)) {
            signText = "&";
        }
        else if ("||".equals(signText)) {
            signText = "|";
        }
        return lhs.getText() + ' ' + signText + "= " + rhsRhs.getText();
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ReplaceAssignmentWithOperatorAssignmentVisitor();
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new ReplaceAssignmentWithOperatorAssignmentFix((GrAssignmentExpression) location);
    }

    private static class ReplaceAssignmentWithOperatorAssignmentFix extends GroovyFix {
        @Nonnull
        private final LocalizeValue myName;

        private ReplaceAssignmentWithOperatorAssignmentFix(GrAssignmentExpression expression) {
            super();
            GrExpression rhs = expression.getRValue();
            GrBinaryExpression binaryExpression = (GrBinaryExpression) PsiUtil.skipParentheses(rhs, false);
            assert binaryExpression != null;
            IElementType sign = binaryExpression.getOperationTokenType();
            String signText = getTextForOperator(sign);
            if ("&&".equals(signText)) {
                signText = "&";
            }
            else if ("||".equals(signText)) {
                signText = "|";
            }
            myName = LocalizeValue.localizeTODO("Replace '=' with '" + signText + "='");
        }

        @Nonnull
        @Override
        public LocalizeValue getName() {
            return myName;
        }

        @Override
        @RequiredWriteAction
        public void doFix(@Nonnull Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            if (!(descriptor.getPsiElement() instanceof GrAssignmentExpression expression)) {
                return;
            }
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ReplaceAssignmentWithOperatorAssignmentVisitor
        extends BaseInspectionVisitor<GroovyAssignmentCanBeOperatorAssignmentInspectionState> {
        private static final Set<IElementType> LAZY_LOGICAL_OPERATORS = Set.of(GroovyTokenTypes.mLAND, GroovyTokenTypes.mLOR);
        private static final Set<IElementType> OBSCURE_OPERATORS = Set.of(GroovyTokenTypes.mBXOR, GroovyTokenTypes.mMOD);

        @Override
        public void visitAssignmentExpression(@Nonnull GrAssignmentExpression assignment) {
            super.visitAssignmentExpression(assignment);
            IElementType assignmentTokenType = assignment.getOperationTokenType();
            if (!assignmentTokenType.equals(GroovyTokenTypes.mASSIGN)) {
                return;
            }
            GrExpression lhs = assignment.getLValue();
            GrExpression rhs = (GrExpression) PsiUtil.skipParentheses(assignment.getRValue(), false);
            if (!(rhs instanceof GrBinaryExpression binaryRhs && binaryRhs.getRightOperand() != null)) {
                return;
            }
            IElementType expressionTokenType = binaryRhs.getOperationTokenType();
            if (getTextForOperator(expressionTokenType) == null) {
                return;
            }
            if (JavaTokenType.EQEQ.equals(expressionTokenType)) {
                return;
            }
            if (myState.ignoreLazyOperators && LAZY_LOGICAL_OPERATORS.contains(expressionTokenType)) {
                return;
            }
            if (myState.ignoreObscureOperators && OBSCURE_OPERATORS.contains(expressionTokenType)) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(lhs)) {
                return;
            }
            if (!EquivalenceChecker.expressionsAreEquivalent(lhs, binaryRhs.getLeftOperand())) {
                return;
            }
            registerError(assignment, assignment);
        }
    }

    @Nullable
    private static String getTextForOperator(IElementType operator) {
        if (operator == null) {
            return null;
        }
        if (operator.equals(GroovyTokenTypes.mPLUS)) {
            return "+";
        }
        if (operator.equals(GroovyTokenTypes.mMINUS)) {
            return "-";
        }
        if (operator.equals(GroovyTokenTypes.mSTAR)) {
            return "*";
        }
        if (operator.equals(GroovyTokenTypes.mDIV)) {
            return "/";
        }
        if (operator.equals(GroovyTokenTypes.mMOD)) {
            return "%";
        }
        if (operator.equals(GroovyTokenTypes.mBXOR)) {
            return "^";
        }
        if (operator.equals(GroovyTokenTypes.mLAND)) {
            return "&&";
        }
        if (operator.equals(GroovyTokenTypes.mLOR)) {
            return "||";
        }
        if (operator.equals(GroovyTokenTypes.mBAND)) {
            return "&";
        }
        if (operator.equals(GroovyTokenTypes.mBOR)) {
            return "|";
        }
        /*
        if (operator.equals(GroovyTokenTypes.mSR)) {
            return "<<";
        }

        if (operator.equals(GroovyTokenTypes.GTGT)) {
            return ">>";
        }
        if (operator.equals(GroovyTokenTypes.GTGTGT)) {
            return ">>>";
        }
        */
        return null;
    }
}
