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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.impl.codeInspection.GrInspectionUtil.*;

public class GroovyConditionalCanBeConditionalCallInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Conditional expression can be conditional call");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Override
    public String buildErrorString(Object... args) {
        return "Conditional expression can be call #loc";
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new CollapseConditionalFix();
    }

    private static class CollapseConditionalFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Replace with conditional call");
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
            GrBinaryExpression binaryCondition = (GrBinaryExpression) PsiUtil.skipParentheses(expression.getCondition(), false);
            GrMethodCallExpression call;
            if (isInequality(binaryCondition)) {
                call = (GrMethodCallExpression) expression.getThenBranch();
            }
            else {
                call = (GrMethodCallExpression) expression.getElseBranch();
            }
            GrReferenceExpression methodExpression = (GrReferenceExpression) call.getInvokedExpression();
            GrExpression qualifier = methodExpression.getQualifierExpression();
            String methodName = methodExpression.getReferenceName();
            GrArgumentList argumentList = call.getArgumentList();
            if (argumentList == null) {
                return;
            }
            replaceExpression(expression, qualifier.getText() + "?." + methodName + argumentList.getText());
        }
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private static class Visitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitConditionalExpression(GrConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            GrExpression condition = expression.getCondition();
            GrExpression thenBranch = expression.getThenBranch();
            GrExpression elseBranch = expression.getElseBranch();
            if (thenBranch == null || elseBranch == null) {
                return;
            }
            if (SideEffectChecker.mayHaveSideEffects(condition)) {
                return;
            }
            condition = (GrExpression) PsiUtil.skipParentheses(condition, false);
            if (!(condition instanceof GrBinaryExpression binaryExpr)) {
                return;
            }
            GrExpression lhs = binaryExpr.getLeftOperand();
            GrExpression rhs = binaryExpr.getRightOperand();
            if (isInequality(binaryExpr) && isNull(elseBranch)) {
                if (isNull(lhs) && isCallTargeting(thenBranch, rhs)
                    || isNull(rhs) && isCallTargeting(thenBranch, lhs)) {
                    registerError(expression);
                }
            }

            if (isEquality(binaryExpr) && isNull(thenBranch)) {
                if (isNull(lhs) && isCallTargeting(elseBranch, rhs)
                    || isNull(rhs) && isCallTargeting(elseBranch, lhs)) {
                    registerError(expression);
                }
            }
        }

        private static boolean isCallTargeting(GrExpression call, GrExpression expression) {
            return call instanceof GrMethodCallExpression callExpr
                && callExpr.getInvokedExpression() instanceof GrReferenceExpression methodRef
                && GroovyTokenTypes.mDOT.equals(methodRef.getDotTokenType())
                && EquivalenceChecker.expressionsAreEquivalent(expression, methodRef.getQualifierExpression());
        }
    }
}