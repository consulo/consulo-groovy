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

import consulo.language.ast.IElementType;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.SingleIntegerFieldOptionsPanel;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import javax.swing.*;

public class GroovyOverlyComplexBooleanExpressionInspection extends BaseInspection {
    private static final int TERM_LIMIT = 3;

    /**
     * @noinspection PublicField, WeakerAccess
     */
    public int myLimit = TERM_LIMIT;

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Overly complex boolean expression");
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    private int getLimit() {
        return myLimit;
    }

    public JComponent createOptionsPanel() {
        return new SingleIntegerFieldOptionsPanel("Maximum number of terms:", this, "m_limit");
    }

    @Override
    protected String buildErrorString(Object... args) {
        return "Overly complex boolean expression #loc";
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    private class Visitor extends BaseInspectionVisitor {
        @Override
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitUnaryExpression(@Nonnull GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);
            checkExpression(expression);
        }

        @Override
        public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
            super.visitParenthesizedExpression(expression);
            checkExpression(expression);
        }

        private void checkExpression(GrExpression expression) {
            if (!isBoolean(expression)) {
                return;
            }
            if (isParentBoolean(expression)) {
                return;
            }
            int numTerms = countTerms(expression);
            if (numTerms <= getLimit()) {
                return;
            }
            registerError(expression);
        }

        private int countTerms(GrExpression expression) {
            if (expression == null) {
                return 0;
            }
            if (!isBoolean(expression)) {
                return 1;
            }
            if (expression instanceof GrBinaryExpression binaryExpr) {
                return countTerms(binaryExpr.getLeftOperand()) + countTerms(binaryExpr.getRightOperand());
            }
            else if (expression instanceof GrUnaryExpression prefixExpr) {
                return countTerms(prefixExpr.getOperand());
            }
            else if (expression instanceof GrParenthesizedExpression parenthesized) {
                return countTerms(parenthesized.getOperand());
            }
            return 1;
        }

        private boolean isParentBoolean(GrExpression expression) {
            return expression.getParent() instanceof GrExpression expr && isBoolean(expr);
        }

        private boolean isBoolean(GrExpression expression) {
            if (expression instanceof GrBinaryExpression binaryExpr) {
                IElementType sign = binaryExpr.getOperationTokenType();
                return GroovyTokenTypes.mLAND.equals(sign)
                    || GroovyTokenTypes.mLOR.equals(sign);
            }
            else if (expression instanceof GrUnaryExpression prefixExpr) {
                return GroovyTokenTypes.mLNOT.equals(prefixExpr.getOperationTokenType());
            }
            else if (expression instanceof GrParenthesizedExpression parenthesized) {
                return isBoolean(parenthesized.getOperand());
            }
            return false;
        }
    }
}