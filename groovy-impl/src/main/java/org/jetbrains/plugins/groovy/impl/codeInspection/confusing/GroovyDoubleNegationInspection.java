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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import consulo.annotation.access.RequiredWriteAction;
import consulo.language.ast.IElementType;
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyDoubleNegationInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Double negation");
    }

    @Nonnull
    @Override
    protected String buildErrorString(Object... infos) {
        return "Double negation #ref #loc";
    }

    @Nullable
    @Override
    protected GroovyFix buildFix(@Nonnull PsiElement location) {
        return new DoubleNegationFix();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    private static class DoubleNegationFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Remove double negation");
        }

        @Override
        @RequiredWriteAction
        protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
            GrUnaryExpression expression = (GrUnaryExpression) descriptor.getPsiElement();
            GrExpression operand = (GrExpression) PsiUtil.skipParentheses(expression.getOperand(), false);
            if (operand instanceof GrUnaryExpression prefixExpression) {
                GrExpression innerOperand = prefixExpression.getOperand();
                if (innerOperand == null) {
                    return;
                }
                replaceExpression(expression, innerOperand.getText());
            }
            else if (operand instanceof GrBinaryExpression binaryExpression) {
                GrExpression lhs = binaryExpression.getLeftOperand();
                String lhsText = lhs.getText();
                StringBuilder builder = new StringBuilder(lhsText);
                builder.append("==");
                GrExpression rhs = binaryExpression.getRightOperand();
                if (rhs != null) {
                    String rhsText = rhs.getText();
                    builder.append(rhsText);
                }
                replaceExpression(expression, builder.toString());
            }
        }
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new DoubleNegationVisitor();
    }

    private static class DoubleNegationVisitor extends BaseInspectionVisitor {
        @Override
        public void visitUnaryExpression(GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!GroovyTokenTypes.mLNOT.equals(tokenType)) {
                return;
            }
            checkParent(expression);
        }

        @Override
        public void visitBinaryExpression(GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            IElementType tokenType = expression.getOperationTokenType();
            if (!GroovyTokenTypes.mNOT_EQUAL.equals(tokenType)) {
                return;
            }
            checkParent(expression);
        }

        private void checkParent(GrExpression expression) {
            PsiElement parent = expression.getParent();
            while (parent instanceof GrParenthesizedExpression) {
                parent = parent.getParent();
            }
            if (!(parent instanceof GrUnaryExpression prefixExpression)) {
                return;
            }
            IElementType parentTokenType = prefixExpression.getOperationTokenType();
            if (!GroovyTokenTypes.mLNOT.equals(parentTokenType)) {
                return;
            }
            registerError(prefixExpression);
        }
    }
}