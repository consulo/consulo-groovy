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

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class GroovyConditionalCanBeElvisInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Conditional expression can be elvis");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Override
    public String buildErrorString(Object... args) {
        return "Conditional expression can be elvis #loc";
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new GroovyFix() {
            @Nonnull
            @Override
            public LocalizeValue getName() {
                return LocalizeValue.localizeTODO("Convert Conditional to Elvis");
            }

            @Override
            @RequiredWriteAction
            public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
                GrConditionalExpression expr = (GrConditionalExpression) descriptor.getPsiElement();

                GrExpression condition = expr.getCondition();
                GrExpression thenExpression = expr.getThenBranch();
                GrExpression elseExpression = expr.getElseBranch();
                assert elseExpression != null;
                assert thenExpression != null;

                String newExpression;
                if (checkForStringIsEmpty(condition, elseExpression)
                    || checkForListIsEmpty(condition, elseExpression)
                    || checkForEqualsNotElse(condition, elseExpression)) {
                    newExpression = elseExpression.getText() + " ?: " + thenExpression.getText();
                }
                else {
                    newExpression = thenExpression.getText() + " ?: " + elseExpression.getText();
                }
                PsiImplUtil.replaceExpression(newExpression, expr);
            }
        };
    }

    @RequiredReadAction
    private static boolean checkPsiElement(GrConditionalExpression expr) {
        if (expr instanceof GrElvisExpression) {
            return false;
        }
        GrExpression condition = expr.getCondition();

        GrExpression then = expr.getThenBranch();
        GrExpression elseBranch = expr.getElseBranch();
        if (then == null || elseBranch == null) {
            return false;
        }

        return checkForEqualsThen(condition, then)
            || checkForEqualsNotElse(condition, elseBranch)
            || checkForNull(condition, then)
            || checkForStringIsEmpty(condition, elseBranch)
            || checkForStringIsNotEmpty(condition, then)
            || checkForListIsEmpty(condition, elseBranch)
            || checkForListIsNotEmpty(condition, then);
    }

    private static boolean checkForEqualsNotElse(GrExpression condition, GrExpression elseBranch) {
        if (!(condition instanceof GrUnaryExpression prefixExpr)) {
            return false;
        }
        if (prefixExpr.getOperationTokenType() != GroovyTokenTypes.mLNOT) {
            return false;
        }

        GrExpression operand = prefixExpr.getOperand();
        return operand != null && PsiEquivalenceUtil.areElementsEquivalent(operand, elseBranch);
    }

    private static boolean checkForEqualsThen(GrExpression condition, GrExpression then) {
        return PsiEquivalenceUtil.areElementsEquivalent(condition, then);
    }

    private static boolean checkForListIsNotEmpty(GrExpression condition, GrExpression then) {
        if (!(condition instanceof GrUnaryExpression prefixExpr)) {
            return false;
        }

        if (prefixExpr.getOperationTokenType() != GroovyTokenTypes.mLNOT) {
            return false;
        }

        return checkForListIsEmpty(((GrUnaryExpression) condition).getOperand(), then);
    }

    private static boolean checkForListIsEmpty(GrExpression condition, GrExpression elseBranch) {
        if (condition instanceof GrMethodCall methodCall) {
            condition = methodCall.getInvokedExpression();
        }
        if (!(condition instanceof GrReferenceExpression conditionRef)) {
            return false;
        }

        GrExpression qualifier = conditionRef.getQualifier();
        if (qualifier == null) {
            return false;
        }

        if (!PsiEquivalenceUtil.areElementsEquivalent(qualifier, elseBranch)) {
            return false;
        }

        PsiType type = qualifier.getType();
        if (type == null) {
            return false;
        }
        if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) {
            return false;
        }

        PsiElement resolved = conditionRef.resolve();

        return resolved instanceof PsiMethod method
            && "isEmpty".equals(method.getName())
            && method.getParameterList().getParametersCount() == 0;
    }

    /**
     * checks for the case !string.isEmpty ? string : something_else
     */
    private static boolean checkForStringIsNotEmpty(GrExpression condition, GrExpression then) {
        return condition instanceof GrUnaryExpression prefixExpr
            && prefixExpr.getOperationTokenType() == GroovyTokenTypes.mLNOT
            && checkForStringIsEmpty(prefixExpr.getOperand(), then);
    }

    /**
     * checks for the case string.isEmpty() ? something_else : string
     */
    private static boolean checkForStringIsEmpty(GrExpression condition, GrExpression elseBranch) {
        if (condition instanceof GrMethodCall methodCall) {
            condition = methodCall.getInvokedExpression();
        }
        if (!(condition instanceof GrReferenceExpression conditionRef)) {
            return false;
        }

        GrExpression qualifier = conditionRef.getQualifier();
        if (qualifier == null) {
            return false;
        }

        if (!PsiEquivalenceUtil.areElementsEquivalent(qualifier, elseBranch)) {
            return false;
        }

        PsiType type = qualifier.getType();
        if (type == null) {
            return false;
        }
        if (!type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return false;
        }

        PsiElement resolved = conditionRef.resolve();

        return resolved instanceof PsiMethod method
            && "isEmpty".equals(method.getName())
            && method.getParameterList().getParametersCount() == 0;
    }

    @RequiredReadAction
    private static boolean checkForNull(GrExpression condition, GrExpression then) {
        if (!(condition instanceof GrBinaryExpression)) {
            return false;
        }

        GrBinaryExpression binaryExpression = (GrBinaryExpression) condition;
        if (GroovyTokenTypes.mNOT_EQUAL != binaryExpression.getOperationTokenType()) {
            return false;
        }

        GrExpression left = binaryExpression.getLeftOperand();
        GrExpression right = binaryExpression.getRightOperand();
        if (left instanceof GrLiteral lLiteral && "null".equals(lLiteral.getText()) && right != null) {
            return PsiEquivalenceUtil.areElementsEquivalent(right, then);
        }
        if (right instanceof GrLiteral rLiteral && "null".equals(rLiteral.getText())) {
            return PsiEquivalenceUtil.areElementsEquivalent(left, then);
        }

        return false;
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
            if (checkPsiElement(expression)) {
                registerError(expression);
            }
        }
    }
}