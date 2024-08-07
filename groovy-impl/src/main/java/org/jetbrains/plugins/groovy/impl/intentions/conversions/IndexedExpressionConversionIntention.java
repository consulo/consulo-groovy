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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.codeEditor.Editor;
import consulo.project.Project;

public class IndexedExpressionConversionIntention extends Intention
{

	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new IndexedExpressionConversionPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{

		final GrIndexProperty arrayIndexExpression = (GrIndexProperty) element;

		final GrArgumentList argList = (GrArgumentList) arrayIndexExpression.getLastChild();

		assert argList != null;
		final GrExpression[] arguments = argList.getExpressionArguments();

		final PsiElement parent = element.getParent();
		final GrExpression arrayExpression = arrayIndexExpression.getInvokedExpression();
		if(!(parent instanceof GrAssignmentExpression))
		{
			rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
			return;
		}
		final GrAssignmentExpression assignmentExpression = (GrAssignmentExpression) parent;
		final GrExpression rhs = assignmentExpression.getRValue();
		if(rhs.equals(element))
		{
			rewriteAsGetAt(arrayIndexExpression, arrayExpression, arguments[0]);
		}
		else
		{
			rewriteAsSetAt(assignmentExpression, arrayExpression, arguments[0], rhs);
		}
	}

	private static void rewriteAsGetAt(GrIndexProperty arrayIndexExpression,
			GrExpression arrayExpression,
			GrExpression argument) throws IncorrectOperationException
	{
		PsiImplUtil.replaceExpression(arrayExpression.getText() + ".getAt(" + argument.getText() + ')',
				arrayIndexExpression);
	}

	private static void rewriteAsSetAt(GrAssignmentExpression assignment,
			GrExpression arrayExpression,
			GrExpression argument,
			GrExpression value) throws IncorrectOperationException
	{
		PsiImplUtil.replaceExpression(arrayExpression.getText() + ".putAt(" + argument.getText() + ", " +
				"" + value.getText() + ')', assignment);
	}

}
