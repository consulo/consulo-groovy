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
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
public interface GroovyResolveResult extends ResolveResult
{
	GroovyResolveResult[] EMPTY_ARRAY = new GroovyResolveResult[0];

	boolean isAccessible();

	boolean isStaticsOK();

	boolean isApplicable();

	@Nullable
	PsiElement getCurrentFileResolveContext();

	@Nonnull
	PsiSubstitutor getSubstitutor();

	boolean isInvokedOnProperty();

	@Nullable
	SpreadState getSpreadState();

	GroovyResolveResult EMPTY_RESULT = new GroovyResolveResult()
	{
		@Override
		public boolean isAccessible()
		{
			return false;
		}

		@Override
		public PsiElement getCurrentFileResolveContext()
		{
			return null;
		}

		@Override
		public boolean isStaticsOK()
		{
			return true;
		}

		@Override
		public boolean isApplicable()
		{
			return false;
		}

		@Override
		@Nonnull
		public PsiSubstitutor getSubstitutor()
		{
			return PsiSubstitutor.EMPTY;
		}

		@Override
		@Nullable
		public PsiElement getElement()
		{
			return null;
		}

		@Override
		public boolean isValidResult()
		{
			return false;
		}

		@Override
		public boolean isInvokedOnProperty()
		{
			return false;
		}

		@Override
		public SpreadState getSpreadState()
		{
			return null;
		}
	};
}
