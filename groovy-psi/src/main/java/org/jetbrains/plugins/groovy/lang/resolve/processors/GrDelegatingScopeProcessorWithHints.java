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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import java.util.EnumSet;

import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Created by Max Medvedev on 31/03/14
 */
public class GrDelegatingScopeProcessorWithHints extends GrScopeProcessorWithHints
{
	private final PsiScopeProcessor myDelegate;

	public GrDelegatingScopeProcessorWithHints(@Nonnull PsiScopeProcessor delegate,
			@Nullable String name,
			@Nullable EnumSet<ClassHint.ResolveKind> resolveTargets)
	{
		super(name, resolveTargets);
		myDelegate = delegate;
	}

	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		return myDelegate.execute(element, state);
	}

	@Override
	public void handleEvent(@Nonnull PsiScopeProcessor.Event event, @Nullable Object associated)
	{
		myDelegate.handleEvent(event, associated);
	}

	@Override
	public <T> T getHint(@Nonnull Key<T> hintKey)
	{
		T hint = super.getHint(hintKey);
		if(hint != null)
		{
			return hint;
		}

		return myDelegate.getHint(hintKey);
	}
}
