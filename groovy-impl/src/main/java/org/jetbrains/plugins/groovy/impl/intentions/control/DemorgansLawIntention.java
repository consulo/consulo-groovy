/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.MutablyNamedIntention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class DemorgansLawIntention extends MutablyNamedIntention {
    protected LocalizeValue getTextForElement(PsiElement element) {
        GrBinaryExpression binaryExpression = (GrBinaryExpression) element;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        if (GroovyTokenTypes.mLAND.equals(tokenType)) {
            return GroovyIntentionLocalize.demorgansIntentionName1();
        }
        else {
            return GroovyIntentionLocalize.demorgansIntentionName2();
        }
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ConjunctionPredicate();
    }

    public void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrBinaryExpression exp = (GrBinaryExpression) element;
        IElementType tokenType = exp.getOperationTokenType();
        PsiElement parent = exp.getParent();
        while (isConjunctionExpression(parent, tokenType)) {
            exp = (GrBinaryExpression) parent;
            assert exp != null;
            parent = exp.getParent();
        }
        String newExpression = convertConjunctionExpression(exp, tokenType);
        replaceExpressionWithNegatedExpressionString(newExpression, exp);
    }

    private static String convertConjunctionExpression(GrBinaryExpression exp, IElementType tokenType) {
        GrExpression lhs = exp.getLeftOperand();
        String lhsText;
        if (isConjunctionExpression(lhs, tokenType)) {
            lhsText = convertConjunctionExpression((GrBinaryExpression) lhs, tokenType);
        }
        else {
            lhsText = convertLeafExpression(lhs);
        }
        GrExpression rhs = exp.getRightOperand();
        String rhsText;
        if (isConjunctionExpression(rhs, tokenType)) {
            rhsText = convertConjunctionExpression((GrBinaryExpression) rhs, tokenType);
        }
        else {
            rhsText = convertLeafExpression(rhs);
        }

        String flippedConjunction;
        if (tokenType.equals(GroovyTokenTypes.mLAND)) {
            flippedConjunction = "||";
        }
        else {
            flippedConjunction = "&&";
        }

        return lhsText + flippedConjunction + rhsText;
    }

    private static String convertLeafExpression(GrExpression condition) {
        if (BoolUtils.isNegation(condition)) {
            GrExpression negated = BoolUtils.getNegated(condition);
            if (ParenthesesUtils.getPrecedence(negated) > ParenthesesUtils.OR_PRECEDENCE) {
                return '(' + negated.getText() + ')';
            }
            return negated.getText();
        }
        else if (ComparisonUtils.isComparison(condition)) {
            GrBinaryExpression binaryExpression = (GrBinaryExpression) condition;
            IElementType sign = binaryExpression.getOperationTokenType();
            String negatedComparison = ComparisonUtils.getNegatedComparison(sign);
            GrExpression lhs = binaryExpression.getLeftOperand();
            GrExpression rhs = binaryExpression.getRightOperand();
            assert rhs != null;
            return lhs.getText() + negatedComparison + rhs.getText();
        }
        else if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.PREFIX_PRECEDENCE) {
            return "!(" + condition.getText() + ')';
        }
        else {
            return '!' + condition.getText();
        }
    }

    private static boolean isConjunctionExpression(PsiElement exp, IElementType conjunctionType) {
        if (!(exp instanceof GrBinaryExpression)) {
            return false;
        }
        GrBinaryExpression binExp = (GrBinaryExpression) exp;
        IElementType tokenType = binExp.getOperationTokenType();
        return conjunctionType.equals(tokenType);
    }
}
