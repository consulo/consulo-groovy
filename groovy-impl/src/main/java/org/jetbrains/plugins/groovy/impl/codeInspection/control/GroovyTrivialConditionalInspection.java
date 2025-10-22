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

import com.intellij.java.language.psi.PsiType;
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
import org.jetbrains.plugins.groovy.impl.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.impl.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyTrivialConditionalInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Redundant conditional expression");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONTROL_FLOW;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryConditionalExpressionVisitor();
    }

    @Override
    @RequiredReadAction
    public String buildErrorString(Object... args) {
        GrConditionalExpression exp = (GrConditionalExpression) args[0];
        return "'" + exp.getText() + "' can be simplified to '" + calculateReplacementExpression(exp) + "'  #loc";
    }

    @RequiredReadAction
    private static String calculateReplacementExpression(GrConditionalExpression exp) {
        GrExpression thenExpression = exp.getThenBranch();
        GrExpression elseExpression = exp.getElseBranch();
        GrExpression condition = exp.getCondition();

        if (isFalse(thenExpression) && isTrue(elseExpression)) {
            return BoolUtils.getNegatedExpressionText(condition);
        }
        else {
            return condition.getText();
        }
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new TrivialConditionalFix();
    }

    private static class TrivialConditionalFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Simplify");
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrConditionalExpression expression = (GrConditionalExpression) descriptor.getPsiElement();
            String newExpression = calculateReplacementExpression(expression);
            replaceExpression(expression, newExpression);
        }
    }

    private static class UnnecessaryConditionalExpressionVisitor extends BaseInspectionVisitor {
        @Override
        @RequiredReadAction
        public void visitConditionalExpression(GrConditionalExpression exp) {
            super.visitConditionalExpression(exp);
            GrExpression condition = exp.getCondition();
            PsiType type = condition.getType();
            if (type == null || !(PsiType.BOOLEAN.isAssignableFrom(type))) {
                return;
            }

            if (ErrorUtil.containsError(exp)) {
                return;
            }

            GrExpression thenExpression = exp.getThenBranch();
            if (thenExpression == null) {
                return;
            }
            GrExpression elseExpression = exp.getElseBranch();
            if (elseExpression == null) {
                return;
            }
            if ((isFalse(thenExpression) && isTrue(elseExpression))
                || (isTrue(thenExpression) && isFalse(elseExpression))) {
                registerError(exp);
            }
        }
    }

    @RequiredReadAction
    private static boolean isFalse(GrExpression expression) {
        return "false".equals(expression.getText());
    }

    @RequiredReadAction
    private static boolean isTrue(GrExpression expression) {
        return "true".equals(expression.getText());
    }
}
