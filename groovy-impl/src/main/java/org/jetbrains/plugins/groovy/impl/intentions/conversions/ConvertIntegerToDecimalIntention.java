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

import java.math.BigInteger;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import consulo.language.util.IncorrectOperationException;

public class ConvertIntegerToDecimalIntention extends Intention
{

	@Nonnull
	public PsiElementPredicate getElementPredicate()
	{
		return new ConvertIntegerToDecimalPredicate();
	}

	public void processIntention(@Nonnull PsiElement element,
			Project project,
			Editor editor) throws IncorrectOperationException
	{
		final GrLiteral exp = (GrLiteral) element;
		@NonNls String textString = exp.getText().replaceAll("_", "");
		final int textLength = textString.length();
		final char lastChar = textString.charAt(textLength - 1);
		final boolean isLong = lastChar == 'l' || lastChar == 'L';
		if(isLong)
		{
			textString = textString.substring(0, textLength - 1);
		}
		final BigInteger val;
		if(textString.startsWith("0x") || textString.startsWith("0X"))
		{
			final String rawIntString = textString.substring(2);
			val = new BigInteger(rawIntString, 16);
		}
		else if(textString.startsWith("0b") || textString.startsWith("0B"))
		{
			final String rawString = textString.substring(2);
			val = new BigInteger(rawString, 2);
		}
		else
		{
			final String rawIntString = textString.substring(1);
			val = new BigInteger(rawIntString, 8);
		}
		String decimalString = val.toString(10);
		if(isLong)
		{
			decimalString += 'L';
		}
		PsiImplUtil.replaceExpression(decimalString, exp);
	}
}
