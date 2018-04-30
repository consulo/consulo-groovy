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
package org.jetbrains.plugins.groovy.codeInspection;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * @author Max Medvedev
 */
public class GrInspectionUtil
{
	public static boolean isNull(@Nonnull GrExpression expression)
	{
		return "null".equals(expression.getText());
	}

	public static boolean isEquality(@Nonnull GrBinaryExpression binaryCondition)
	{
		final IElementType tokenType = binaryCondition.getOperationTokenType();
		return GroovyTokenTypes.mEQUAL == tokenType;
	}

	public static boolean isInequality(@Nonnull GrBinaryExpression binaryCondition)
	{
		final IElementType tokenType = binaryCondition.getOperationTokenType();
		return GroovyTokenTypes.mNOT_EQUAL == tokenType;
	}

	public static HighlightInfo createAnnotationForRef(@Nonnull GrReferenceElement ref,
			@Nonnull HighlightDisplayLevel displayLevel,
			@Nonnull String message)
	{
		PsiElement refNameElement = ref.getReferenceNameElement();
		assert refNameElement != null;

		if(displayLevel == HighlightDisplayLevel.ERROR)
		{
			return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(refNameElement)
					.descriptionAndTooltip(message).create();
		}

		if(displayLevel == HighlightDisplayLevel.WEAK_WARNING)
		{
			boolean isTestMode = ApplicationManager.getApplication().isUnitTestMode();
			HighlightInfoType infotype = isTestMode ? HighlightInfoType.WARNING : HighlightInfoType.INFORMATION;

			HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(infotype).range(refNameElement);
			builder.descriptionAndTooltip(message);
			return builder.needsUpdateOnTyping(false).textAttributes(GroovySyntaxHighlighter.UNRESOLVED_ACCESS)
					.create();
		}

		HighlightInfoType highlightInfoType = HighlightInfo.convertSeverity(displayLevel.getSeverity());
		return HighlightInfo.newHighlightInfo(highlightInfoType).range(refNameElement).descriptionAndTooltip(message)
				.create();
	}
}
