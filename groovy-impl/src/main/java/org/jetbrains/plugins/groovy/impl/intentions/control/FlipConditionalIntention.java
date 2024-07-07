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
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.intentions.utils.BoolUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.project.Project;

public class FlipConditionalIntention extends Intention
{


	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new ConditionalPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final GrConditionalExpression exp = (GrConditionalExpression) element;

		final GrExpression condition = exp.getCondition();
		final GrExpression elseExpression = exp.getElseBranch();
		final GrExpression thenExpression = exp.getThenBranch();
		assert elseExpression != null;
		assert thenExpression != null;
		final String newExpression = BoolUtils.getNegatedExpressionText(condition) + '?' +
				elseExpression.getText() +
				':' +
				thenExpression.getText();
		PsiImplUtil.replaceExpression(newExpression, exp);
	}

}
