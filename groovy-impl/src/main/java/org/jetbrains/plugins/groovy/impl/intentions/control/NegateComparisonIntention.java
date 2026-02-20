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
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class NegateComparisonIntention extends MutablyNamedIntention {
    protected LocalizeValue getTextForElement(PsiElement element) {
        GrBinaryExpression binaryExpression = (GrBinaryExpression) element;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        String comparison = ComparisonUtils.getStringForComparison(tokenType);
        String negatedComparison = ComparisonUtils.getNegatedComparison(tokenType);

        return GroovyIntentionLocalize.negateComparisonIntentionName(comparison, negatedComparison);
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ComparisonPredicate();
    }

    public void processIntention(@Nonnull PsiElement element, Project project, Editor editor)
        throws IncorrectOperationException {
        GrBinaryExpression exp =
            (GrBinaryExpression) element;
        IElementType tokenType = exp.getOperationTokenType();

        GrExpression lhs = exp.getLeftOperand();
        String lhsText = lhs.getText();

        GrExpression rhs = exp.getRightOperand();
        String rhsText = rhs.getText();

        String negatedComparison = ComparisonUtils.getNegatedComparison(tokenType);

        String newExpression = lhsText + negatedComparison + rhsText;
        replaceExpressionWithNegatedExpressionString(newExpression, exp);
    }
}
