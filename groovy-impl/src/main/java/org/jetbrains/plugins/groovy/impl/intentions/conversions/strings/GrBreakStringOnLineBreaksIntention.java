/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.conversions.strings;

import jakarta.annotation.Nonnull;

import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import consulo.codeEditor.Editor;
import consulo.language.util.IncorrectOperationException;

/**
 * @author Max Medvedev
 */
public class GrBreakStringOnLineBreaksIntention extends Intention
{
	@Override
	protected void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final String text = invokeImpl(element);
		final GrExpression newExpr = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(text);
		((GrExpression) element).replaceWithExpression(newExpr, true);
	}

	@Nonnull
	@Override
	protected PsiElementPredicate getElementPredicate()
	{
		return new PsiElementPredicate()
		{
			@Override
			public boolean satisfiedBy(PsiElement element)
			{
				return element instanceof GrLiteral && !element.getText().equals(invokeImpl(element));
			}
		};
	}

	private static String invokeImpl(PsiElement element)
	{
		final String text = element.getText();
		final String quote = GrStringUtil.getStartQuote(text);

		if(!("'".equals(quote) || "\"".equals(quote)))
		{
			return text;
		}
		if(!text.contains("\\n"))
		{
			return text;
		}

		String value = GrStringUtil.removeQuotes(text);

		StringBuilder buffer = new StringBuilder();
		if(element instanceof GrString)
		{
			processGString(element, quote, value, buffer);
		}
		else
		{
			processSimpleString(quote, value, buffer);
		}

		final String result = buffer.toString();
		if(result.endsWith("+\n\"\""))
		{
			return result.substring(0, result.length() - 4);
		}

		return result;
	}

	private static void processGString(PsiElement element, String quote, String value, StringBuilder buffer)
	{
		final ASTNode node = element.getNode();

		for(ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext())
		{
			final IElementType type = child.getElementType();
			if(type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END)
			{
				continue;
			}
			if(type == GroovyElementTypes.GSTRING_INJECTION)
			{
				buffer.append(child.getText());
			}
			else
			{
				value = child.getText();
				int prev = 0;
				if(!isInjection(child.getTreePrev()))
				{
					buffer.append(quote);
				}
				for(int pos = value.indexOf("\\n"); pos >= 0; pos = value.indexOf("\\n", prev))
				{
					int end = checkForR(value, pos);
					buffer.append(value.substring(prev, end));
					prev = end;
					buffer.append(quote);
					buffer.append("+\n");
					buffer.append(quote);
				}
				buffer.append(value.substring(prev, value.length()));
				if(!isInjection(child.getTreeNext()))
				{
					buffer.append(quote);
				}
			}
		}
	}

	private static boolean isInjection(ASTNode next)
	{
		return next != null && next.getElementType() == GroovyElementTypes.GSTRING_INJECTION;
	}

	private static void processSimpleString(String quote, String value, StringBuilder buffer)
	{
		int prev = 0;
		for(int pos = value.indexOf("\\n"); pos >= 0; pos = value.indexOf("\\n", prev))
		{
			buffer.append(quote);
			int end = checkForR(value, pos);
			buffer.append(value.substring(prev, end));
			prev = end;
			buffer.append(quote);
			buffer.append("+\n");
		}
		buffer.append(quote);
		buffer.append(value.substring(prev, value.length()));
		buffer.append(quote);
	}

	private static int checkForR(String value, int pos)
	{
		pos += 2;
		if(value.length() > pos + 2 && "\r".equals(value.substring(pos, pos + 2)))
		{
			return pos + 2;
		}
		return pos;
	}
}
