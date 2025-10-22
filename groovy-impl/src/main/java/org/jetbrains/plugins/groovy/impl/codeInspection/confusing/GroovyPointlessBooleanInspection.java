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

import com.intellij.java.language.psi.PsiType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
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
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.Set;

public class GroovyPointlessBooleanInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return GroovyInspectionLocalize.pointlessBooleanDisplayName();
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new PointlessBooleanExpressionVisitor();
    }

    @Override
    @RequiredReadAction
    public String buildErrorString(Object... args) {
        if (args[0] instanceof GrBinaryExpression) {
            return GroovyInspectionLocalize.pointlessBooleanProblemDescriptor(
                calculateSimplifiedBinaryExpression((GrBinaryExpression) args[0])
            ).get();
        }
        else {
            return GroovyInspectionLocalize.pointlessBooleanProblemDescriptor(
                calculateSimplifiedPrefixExpression((GrUnaryExpression) args[0])
            ).get();
        }
    }

    @Nullable
    @RequiredReadAction
    private static String calculateSimplifiedBinaryExpression(GrBinaryExpression expression) {
        IElementType sign = expression.getOperationTokenType();
        GrExpression lhs = expression.getLeftOperand();

        GrExpression rhs = expression.getRightOperand();
        if (rhs == null) {
            return null;
        }
        String rhsText = rhs.getText();
        String lhsText = lhs.getText();
        if (sign.equals(GroovyTokenTypes.mLAND)) {
            return isTrue(lhs) ? rhsText : lhsText;
        }
        else if (sign.equals(GroovyTokenTypes.mLOR)) {
            return isFalse(lhs) ? rhsText : lhsText;
        }
        else if (sign.equals(GroovyTokenTypes.mBXOR) ||
            sign.equals(GroovyTokenTypes.mNOT_EQUAL)) {
            if (isFalse(lhs)) {
                return rhsText;
            }
            else if (isFalse(rhs)) {
                return lhsText;
            }
            else if (isTrue(lhs)) {
                return createStringForNegatedExpression(rhs);
            }
            else {
                return createStringForNegatedExpression(lhs);
            }
        }
        else if (sign.equals(GroovyTokenTypes.mEQUAL)) {
            if (isTrue(lhs)) {
                return rhsText;
            }
            else if (isTrue(rhs)) {
                return lhsText;
            }
            else if (isFalse(lhs)) {
                return createStringForNegatedExpression(rhs);
            }
            else {
                return createStringForNegatedExpression(lhs);
            }
        }
        else {
            return "";
        }
    }

    @RequiredReadAction
    private static String createStringForNegatedExpression(GrExpression exp) {
        if (ComparisonUtils.isComparison(exp)) {
            GrBinaryExpression binaryExpression = (GrBinaryExpression) exp;
            IElementType sign = binaryExpression.getOperationTokenType();
            String negatedComparison = ComparisonUtils.getNegatedComparison(sign);
            GrExpression lhs = binaryExpression.getLeftOperand();
            GrExpression rhs = binaryExpression.getRightOperand();
            if (rhs == null) {
                return lhs.getText() + negatedComparison;
            }
            return lhs.getText() + negatedComparison + rhs.getText();
        }
        else {
            String baseText = exp.getText();
            if (ParenthesesUtils.getPrecedence(exp) > ParenthesesUtils.PREFIX_PRECEDENCE) {
                return "!(" + baseText + ')';
            }
            else {
                return '!' + baseText;
            }
        }
    }

    @RequiredReadAction
    private static String calculateSimplifiedPrefixExpression(GrUnaryExpression expression) {
        GrExpression operand = expression.getOperand();
        return isTrue(operand) ? "false" : "true";
    }

    @Override
    public GroovyFix buildFix(@Nonnull PsiElement location) {
        return new BooleanLiteralComparisonFix();
    }

    private static class BooleanLiteralComparisonFix extends GroovyFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return GroovyInspectionLocalize.pointlessBooleanQuickfix();
        }

        @Override
        @RequiredWriteAction
        public void doFix(Project project, ProblemDescriptor descriptor)
            throws IncorrectOperationException {
            PsiElement element = descriptor.getPsiElement();
            if (element instanceof GrBinaryExpression expression) {
                String replacementString = calculateSimplifiedBinaryExpression(expression);
                replaceExpression(expression, replacementString);
            }
            else {
                GrUnaryExpression expression = (GrUnaryExpression) element;
                String replacementString = calculateSimplifiedPrefixExpression(expression);
                replaceExpression(expression, replacementString);
            }
        }
    }

    private static class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {
        private static final Set<IElementType> BOOLEAN_TOKENS = Set.of(
            GroovyTokenTypes.mLAND,
            GroovyTokenTypes.mLOR,
            GroovyTokenTypes.mBXOR,
            GroovyTokenTypes.mEQUAL,
            GroovyTokenTypes.mNOT_EQUAL
        );

        @Override
        @RequiredReadAction
        public void visitBinaryExpression(@Nonnull GrBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            GrExpression rhs = expression.getRightOperand();
            if (rhs == null) {
                return;
            }
            IElementType sign = expression.getOperationTokenType();
            if (!BOOLEAN_TOKENS.contains(sign)) {
                return;
            }

            GrExpression lhs = expression.getLeftOperand();

            boolean isPointless;
            if (sign.equals(GroovyTokenTypes.mEQUAL) ||
                sign.equals(GroovyTokenTypes.mNOT_EQUAL)) {
                isPointless = equalityExpressionIsPointless(lhs, rhs);
            }
            else if (sign.equals(GroovyTokenTypes.mLAND)) {
                isPointless = andExpressionIsPointless(lhs, rhs);
            }
            else if (sign.equals(GroovyTokenTypes.mLOR)) {
                isPointless = orExpressionIsPointless(lhs, rhs);
            }
            else if (sign.equals(GroovyTokenTypes.mBXOR)) {
                isPointless = xorExpressionIsPointless(lhs, rhs);
            }
            else {
                isPointless = false;
            }
            if (!isPointless) {
                return;
            }
            registerError(expression);
        }

        @Override
        @RequiredReadAction
        public void visitUnaryExpression(@Nonnull GrUnaryExpression expression) {
            super.visitUnaryExpression(expression);
            IElementType sign = expression.getOperationTokenType();
            if (sign == null) {
                return;
            }
            GrExpression operand = expression.getOperand();
            if (GroovyTokenTypes.mLNOT.equals(sign) && notExpressionIsPointless(operand)) {
                registerError(expression);
            }
        }
    }

    @RequiredReadAction
    private static boolean equalityExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return (isTrue(lhs) || isFalse(lhs)) && isBoolean(rhs)
            || (isTrue(rhs) || isFalse(rhs)) && isBoolean(lhs);
    }

    private static boolean isBoolean(GrExpression expression) {
        PsiType type = expression.getType();
        PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
        return unboxed != null && PsiType.BOOLEAN.equals(unboxed);
    }

    @RequiredReadAction
    private static boolean andExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return isTrue(lhs) || isTrue(rhs);
    }

    @RequiredReadAction
    private static boolean orExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return isFalse(lhs) || isFalse(rhs);
    }

    @RequiredReadAction
    private static boolean xorExpressionIsPointless(GrExpression lhs, GrExpression rhs) {
        return isTrue(lhs) || isTrue(rhs) || isFalse(lhs) || isFalse(rhs);
    }

    @RequiredReadAction
    private static boolean notExpressionIsPointless(GrExpression arg) {
        return isFalse(arg) || isTrue(arg);
    }

    @RequiredReadAction
    private static boolean isTrue(GrExpression expression) {
        if (expression == null) {
            return false;
        }
        if (!(expression instanceof GrLiteral)) {
            return false;
        }
        String text = expression.getText();
        return "true".equals(text);
    }

    @RequiredReadAction
    private static boolean isFalse(GrExpression expression) {
        return expression instanceof GrLiteral && "false".equals(expression.getText());
    }
}
