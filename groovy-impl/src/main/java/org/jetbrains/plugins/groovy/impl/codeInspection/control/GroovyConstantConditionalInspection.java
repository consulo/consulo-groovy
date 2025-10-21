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
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyConstantConditionalInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Constant conditional expression");
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new ConstantConditionalExpressionVisitor();
    }

    @Nonnull
    @Override
    public String buildErrorString(Object... args) {
        return "'#ref' can be simplified #loc";
    }

    @RequiredReadAction
    static String calculateReplacementExpression(GrConditionalExpression exp) {
        GrExpression thenExpression = exp.getThenBranch();
        GrExpression elseExpression = exp.getElseBranch();
        GrExpression condition = exp.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;
        return isTrue(condition) ? thenExpression.getText() : elseExpression.getText();
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new ConstantConditionalFix();
    }

    private static class ConstantConditionalFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Simplify");
        }

        @Override
        @RequiredReadAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ConstantConditionalExpressionVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitConditionalExpression(GrConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            GrExpression condition = expression.getCondition();
            GrExpression thenExpression = expression.getThenBranch();
            if (thenExpression == null) {
                return;
            }
            GrExpression elseExpression = expression.getElseBranch();
            if (elseExpression == null) {
                return;
            }
            if (isFalse(condition) || isTrue(condition)) {
                registerError(expression, expression);
            }
        }
    }

    @RequiredReadAction
    private static boolean isFalse(GrExpression expression) {
        String text = expression.getText();
        return "false".equals(text);
    }

    @RequiredReadAction
    private static boolean isTrue(GrExpression expression) {
        String text = expression.getText();
        return "true".equals(text);
    }
}
