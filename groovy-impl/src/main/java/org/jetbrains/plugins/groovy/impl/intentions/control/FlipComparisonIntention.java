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

import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.impl.intentions.base.MutablyNamedIntention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.util.IncorrectOperationException;

public class FlipComparisonIntention extends MutablyNamedIntention
{
	protected String getTextForElement(PsiElement element)
	{
		final GrBinaryExpression binaryExpression = (GrBinaryExpression) element;
		final IElementType tokenType = binaryExpression.getOperationTokenType();
		final String comparison = ComparisonUtils.getStringForComparison(tokenType);
		final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

		if(comparison.equals(flippedComparison))
		{
			return GroovyIntentionsBundle.message("flip.smth.intention.name", comparison);
		}

		return GroovyIntentionsBundle.message("flip.comparison.intention.name", comparison, flippedComparison);
	}

	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new ComparisonPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final GrBinaryExpression exp = (GrBinaryExpression) element;
		final IElementType tokenType = exp.getOperationTokenType();

		final GrExpression lhs = exp.getLeftOperand();
		final String lhsText = lhs.getText();

		final GrExpression rhs = exp.getRightOperand();
		final String rhsText = rhs.getText();

		final String flippedComparison = ComparisonUtils.getFlippedComparison(tokenType);

		final String newExpression = rhsText + flippedComparison + lhsText;
		PsiImplUtil.replaceExpression(newExpression, exp);
	}

}
