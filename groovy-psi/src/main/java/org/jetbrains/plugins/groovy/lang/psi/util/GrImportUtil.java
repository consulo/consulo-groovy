/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

import java.util.Collection;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiFile;
import consulo.util.collection.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

/**
 * @author Max Medvedev
 */
public class GrImportUtil
{
	private static final LightCacheKey<MultiMap<String, String>> KEY = LightCacheKey.createByFileModificationCount();

	public static boolean acceptName(GrReferenceElement ref, String expected)
	{
		String actual = ref.getReferenceName();
		if(expected.equals(actual))
		{
			return true;
		}

		if(ref.getQualifier() != null)
		{
			return false;
		}

		PsiFile file = ref.getContainingFile();
		if(file instanceof GroovyFile)
		{
			MultiMap<String, String> data = KEY.getCachedValue(file);
			if(data == null)
			{
				data = collectAliases((GroovyFile) file);
				KEY.putCachedValue(file, data);
			}

			Collection<String> aliases = data.get(expected);
			return aliases.contains(actual);
		}


		return false;
	}

	@Nonnull
	private static MultiMap<String, String> collectAliases(@Nonnull GroovyFile file)
	{
		MultiMap<String, String> aliases = MultiMap.createSet();

		for(GrImportStatement anImport : file.getImportStatements())
		{
			if(anImport.isAliasedImport())
			{
				GrCodeReferenceElement importReference = anImport.getImportReference();
				if(importReference != null)
				{
					String refName = importReference.getReferenceName();
					if(refName != null)
					{
						aliases.putValue(refName, anImport.getImportedName());
					}
				}
			}
		}
		return aliases;
	}
}
