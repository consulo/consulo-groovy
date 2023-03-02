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

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;

public class SplitElseIfIntention extends Intention
{

	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new SplitElseIfPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final GrIfStatement parentStatement = (GrIfStatement) element;
		final GrStatement elseBranch = parentStatement.getElseBranch();
		PsiImplUtil.replaceStatement("if(" + parentStatement.getCondition().getText() + ")" + parentStatement
				.getThenBranch().getText() +
				"else{\n" + elseBranch.getText() + "\n}", parentStatement);
	}
}
