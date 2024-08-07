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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.scope.GlobalSearchScope;

import jakarta.annotation.Nonnull;

/**
 * @author ven
 */
public class GrImmediateTupleType extends GrTupleType
{
	private final PsiType[] myComponentTypes;

	public GrImmediateTupleType(@Nonnull PsiType[] componentTypes,
			@Nonnull JavaPsiFacade facade,
			@Nonnull GlobalSearchScope scope)
	{
		super(scope, facade);
		myComponentTypes = componentTypes;
	}

	@Override
	public boolean isValid()
	{
		for(PsiType initializer : myComponentTypes)
		{
			if(initializer != null && !initializer.isValid())
			{
				return false;
			}
		}
		return true;
	}

	@Nonnull
	@Override
	protected PsiType[] inferComponents()
	{
		return myComponentTypes;
	}

	@Nonnull
	@Override
	public PsiType[] getComponentTypes()
	{
		return myComponentTypes;
	}
}
