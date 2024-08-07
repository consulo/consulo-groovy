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
package org.jetbrains.plugins.groovy.lang.psi.api.signatures;

import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public interface GrMultiSignature extends GrSignature
{
	GrClosureSignature[] getAllSignatures();

	GrMultiSignature EMPTY_SIGNATURE = new GrMultiSignature()
	{
		@Override
		public GrClosureSignature[] getAllSignatures()
		{
			return GrClosureSignature.EMPTY_ARRAY;
		}

		@Override
		public boolean isValid()
		{
			return true;
		}

		@Override
		public GrSignature curry(@Nonnull PsiType[] args, int position, @Nonnull PsiElement context)
		{
			return this;
		}

		@Override
		public void accept(@Nonnull GrSignatureVisitor visitor)
		{
			visitor.visitMultiSignature(this);
		}
	};
}
