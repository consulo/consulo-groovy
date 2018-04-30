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
package org.jetbrains.plugins.groovy.annotator.checkers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by Max Medvedev on 25/03/14
 */
class AliasedAnnotationHolder implements AnnotationHolder
{
	private final AnnotationHolder myHolder;
	private final GrAnnotation myAlias;
	private final GrCodeReferenceElement myReference;

	public AliasedAnnotationHolder(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation alias)
	{
		myHolder = holder;
		myAlias = alias;
		myReference = myAlias.getClassReference();
	}

	@Nonnull
	private PsiElement findCodeElement(@Nonnull PsiElement elt)
	{
		if(PsiTreeUtil.isAncestor(myAlias, elt, true))
		{
			return elt;
		}
		else
		{
			return myReference;
		}
	}

	@Override
	public Annotation createErrorAnnotation(@Nonnull PsiElement elt, @Nullable String message)
	{
		PsiElement codeElement = findCodeElement(elt);
		return myHolder.createErrorAnnotation(codeElement, message);
	}

	@Override
	public Annotation createErrorAnnotation(@Nonnull ASTNode node, @Nullable String message)
	{
		return createErrorAnnotation(node.getPsi(), message);
	}

	@Override
	public Annotation createErrorAnnotation(@Nonnull TextRange range, @javax.annotation.Nullable String message)
	{
		throw new UnsupportedOperationException("unsupported");
	}

	@Override
	public Annotation createWarningAnnotation(@Nonnull PsiElement elt, @Nullable String message)
	{
		return myHolder.createWarningAnnotation(findCodeElement(elt), message);
	}

	@Override
	public Annotation createWarningAnnotation(@Nonnull ASTNode node, @javax.annotation.Nullable String message)
	{
		return myHolder.createWarningAnnotation(node.getPsi(), message);
	}

	@Override
	public Annotation createWarningAnnotation(@Nonnull TextRange range, @Nullable String message)
	{
		throw new UnsupportedOperationException("unsupported");
	}

	@Override
	public Annotation createWeakWarningAnnotation(@Nonnull PsiElement elt, @javax.annotation.Nullable String message)
	{
		return myHolder.createWeakWarningAnnotation(findCodeElement(elt), message);
	}

	@Override
	public Annotation createWeakWarningAnnotation(@Nonnull ASTNode node, @Nullable String message)
	{
		return myHolder.createWarningAnnotation(node.getPsi(), message);
	}

	@Override
	public Annotation createWeakWarningAnnotation(@Nonnull TextRange range, @Nullable String message)
	{
		throw new UnsupportedOperationException("unsupported");
	}

	@Override
	public Annotation createInfoAnnotation(@Nonnull PsiElement elt, @Nullable String message)
	{
		return myHolder.createInfoAnnotation(findCodeElement(elt), message);
	}

	@Override
	public Annotation createInfoAnnotation(@Nonnull ASTNode node, @Nullable String message)
	{
		return myHolder.createInfoAnnotation(node.getPsi(), message);
	}

	@Override
	public Annotation createInfoAnnotation(@Nonnull TextRange range, @javax.annotation.Nullable String message)
	{
		throw new UnsupportedOperationException("unsupported");
	}

	@Override
	public Annotation createAnnotation(@Nonnull HighlightSeverity severity,
			@Nonnull TextRange range,
			@Nullable String message)
	{
		throw new UnsupportedOperationException("unsupported");
	}

	/*@Override
	public Annotation createAnnotation(@NotNull HighlightSeverity severity,
			@NotNull TextRange range,
			@Nullable String message,
			@Nullable String htmlTooltip)
	{
		throw new UnsupportedOperationException("unsupported");
	}  */

	@Nonnull
	@Override
	public AnnotationSession getCurrentAnnotationSession()
	{
		return myHolder.getCurrentAnnotationSession();
	}

	@Override
	public boolean isBatchMode()
	{
		return myHolder.isBatchMode();
	}
}
