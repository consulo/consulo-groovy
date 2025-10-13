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

    public boolean isEnabledByDefault() {
        return true;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ConstantConditionalExpressionVisitor();
    }

    @Nonnull
    public String buildErrorString(Object... args) {
        return "'#ref' can be simplified #loc";
    }

    static String calculateReplacementExpression(GrConditionalExpression exp) {
        final GrExpression thenExpression = exp.getThenBranch();
        final GrExpression elseExpression = exp.getElseBranch();
        final GrExpression condition = exp.getCondition();
        assert thenExpression != null;
        assert elseExpression != null;
        if (isTrue(condition)) {
            return thenExpression.getText();
        } else {
            return elseExpression.getText();
        }
    }

    public GroovyFix buildFix(PsiElement location) {
        return new ConstantConditionalFix();
    }

    private static class ConstantConditionalFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Simplify");
        }

        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            final GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
            final String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class ConstantConditionalExpressionVisitor extends BaseInspectionVisitor {
        public void visitConditionalExpression(GrConditionalExpression expression) {
            super.visitConditionalExpression(expression);
            final GrExpression condition = expression.getCondition();
            final GrExpression thenExpression = expression.getThenBranch();
            if (thenExpression == null) {
                return;
            }
            final GrExpression elseExpression = expression.getElseBranch();
            if (elseExpression == null) {
                return;
            }
            if (isFalse(condition) || isTrue(condition)) {
                registerError(expression, expression);
            }
        }
    }

    private static boolean isFalse(GrExpression expression) {
        final String text = expression.getText();
        return "false".equals(text);
    }

    private static boolean isTrue(GrExpression expression) {
        final String text = expression.getText();
        return "true".equals(text);
    }
}
