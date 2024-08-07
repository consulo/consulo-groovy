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

import jakarta.annotation.Nonnull;

import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.intentions.utils.ConditionalUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;

public class MergeIfAndIntention extends Intention
{


	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new MergeIfAndPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final GrIfStatement parentStatement = (GrIfStatement) element;
		final GrStatement parentThenBranch = parentStatement.getThenBranch();
		final GrIfStatement childStatement = (GrIfStatement) ConditionalUtils.stripBraces(parentThenBranch);

		final GrExpression childCondition = childStatement.getCondition();
		final String childConditionText;
		if(ParenthesesUtils.getPrecedence(childCondition) > ParenthesesUtils.AND_PRECEDENCE)
		{
			childConditionText = '(' + childCondition.getText() + ')';
		}
		else
		{
			childConditionText = childCondition.getText();
		}

		final GrExpression parentCondition = parentStatement.getCondition();
		final String parentConditionText;
		if(ParenthesesUtils.getPrecedence(parentCondition) > ParenthesesUtils.AND_PRECEDENCE)
		{
			parentConditionText = '(' + parentCondition.getText() + ')';
		}
		else
		{
			parentConditionText = parentCondition.getText();
		}

		final GrStatement childThenBranch = childStatement.getThenBranch();
		@NonNls final String statement = "if(" + parentConditionText + "&&" + childConditionText + ')' +
				childThenBranch.getText();
		PsiImplUtil.replaceStatement(statement, parentStatement);
	}
}
