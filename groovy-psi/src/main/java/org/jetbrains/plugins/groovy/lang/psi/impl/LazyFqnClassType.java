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

import javax.annotation.Nonnull;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Created by Max Medvedev on 12/05/14
 */
public class LazyFqnClassType extends GrLiteralClassType
{
	private final String myFqn;

	private LazyFqnClassType(@Nonnull String fqn,
			LanguageLevel languageLevel,
			@Nonnull GlobalSearchScope scope,
			@Nonnull JavaPsiFacade facade)
	{
		super(languageLevel, scope, facade);
		myFqn = fqn;
	}

	@Nonnull
	@Override
	protected String getJavaClassName()
	{
		return myFqn;
	}

	@Nonnull
	@Override
	public String getClassName()
	{
		return StringUtil.getShortName(myFqn);
	}

	@Nonnull
	@Override
	public PsiType[] getParameters()
	{
		return PsiType.EMPTY_ARRAY;
	}

	@Nonnull
	@Override
	public PsiClassType setLanguageLevel(@Nonnull LanguageLevel languageLevel)
	{
		return new LazyFqnClassType(myFqn, languageLevel, getResolveScope(), myFacade);
	}

	@Nonnull
	@Override
	public String getInternalCanonicalText()
	{
		return getJavaClassName();
	}

	@Override
	public boolean isValid()
	{
		return !myFacade.getProject().isDisposed();
	}

	@Nonnull
	@Override
	public PsiClassType rawType()
	{
		return this;
	}

	@Nonnull
	public static PsiClassType getLazyType(@Nonnull String fqn,
			LanguageLevel languageLevel,
			@Nonnull GlobalSearchScope scope,
			@Nonnull JavaPsiFacade facade)
	{
		return new LazyFqnClassType(fqn, languageLevel, scope, facade);
	}

	public static PsiClassType getLazyType(@Nonnull String fqn, @Nonnull PsiElement context)
	{
		return new LazyFqnClassType(fqn, LanguageLevel.JDK_1_5, context.getResolveScope(),
				JavaPsiFacade.getInstance(context.getProject()));
	}
}
