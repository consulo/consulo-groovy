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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public class GroovyResolveResultImpl implements GroovyResolveResult
{
	private final PsiElement myElement;
	private final boolean myIsAccessible;
	private final boolean myIsStaticsOK;
	private final boolean myIsApplicable;

	private final PsiSubstitutor mySubstitutor;
	private final boolean myIsInvokedOnProperty;

	private final PsiElement myCurrentFileResolveContext;
	private final SpreadState mySpreadState;

	public GroovyResolveResultImpl(@Nonnull PsiElement element, boolean isAccessible)
	{
		this(element, null, null, PsiSubstitutor.EMPTY, isAccessible, true, false, true);
	}

	public GroovyResolveResultImpl(@Nonnull PsiElement element,
			@Nullable PsiElement resolveContext,
			@Nullable SpreadState spreadState,
			@Nonnull PsiSubstitutor substitutor,
			boolean isAccessible,
			boolean staticsOK)
	{
		this(element, resolveContext, spreadState, substitutor, isAccessible, staticsOK, false, true);
	}

	public GroovyResolveResultImpl(@Nonnull PsiElement element,
			@Nullable PsiElement resolveContext,
			@Nullable SpreadState spreadState,
			@Nonnull PsiSubstitutor substitutor,
			boolean isAccessible,
			boolean staticsOK,
			boolean isInvokedOnProperty,
			boolean isApplicable)
	{
		myCurrentFileResolveContext = resolveContext;
		myElement = element;
		myIsAccessible = isAccessible;
		mySubstitutor = substitutor;
		myIsStaticsOK = staticsOK;
		myIsInvokedOnProperty = isInvokedOnProperty;
		mySpreadState = spreadState;
		myIsApplicable = isApplicable;
	}

	@Override
	@Nonnull
	public PsiSubstitutor getSubstitutor()
	{
		return mySubstitutor;
	}

	@Override
	public boolean isAccessible()
	{
		return myIsAccessible;
	}

	@Override
	public boolean isStaticsOK()
	{
		return myIsStaticsOK;
	}

	@Override
	public boolean isApplicable()
	{
		return myIsApplicable;
	}

	@Override
	@Nullable
	public PsiElement getElement()
	{
		return myElement;
	}

	@Override
	public boolean isValidResult()
	{
		return isAccessible() && isApplicable() && isStaticsOK();
	}

	public boolean equals(Object o)
	{
		if(this == o)
			return true;
		if(o == null || getClass() != o.getClass())
			return false;

		GroovyResolveResultImpl that = (GroovyResolveResultImpl) o;

		return myIsAccessible == that.myIsAccessible && myElement.getManager().areElementsEquivalent(myElement, that.myElement);
	}

	public int hashCode()
	{
		int result = 0;
		if(myElement instanceof PsiNamedElement)
		{
			String name = ((PsiNamedElement) myElement).getName();
			if(name != null)
			{
				result = name.hashCode();
			}
		}
		result = 31 * result + (myIsAccessible ? 1 : 0);
		return result;
	}

	@Override
	@Nullable
	public PsiElement getCurrentFileResolveContext()
	{
		return myCurrentFileResolveContext;
	}

	@Override
	public boolean isInvokedOnProperty()
	{
		return myIsInvokedOnProperty;
	}

	@Override
	public SpreadState getSpreadState()
	{
		return mySpreadState;
	}

	@Override
	public String toString()
	{
		return "GroovyResolveResultImpl{" +
				"myElement=" + myElement +
				", mySubstitutor=" + mySubstitutor +
				'}';
	}

	@Nonnull
	public static GroovyResolveResult from(@Nonnull PsiClassType.ClassResolveResult classResolveResult)
	{
		if(classResolveResult.getElement() == null)
			return GroovyResolveResult.EMPTY_RESULT;
		return new GroovyResolveResultImpl(classResolveResult.getElement(), null, null, classResolveResult.getSubstitutor(), classResolveResult.isAccessible(), classResolveResult.isStaticsScopeCorrect(), false, classResolveResult.isValidResult());
	}
}
