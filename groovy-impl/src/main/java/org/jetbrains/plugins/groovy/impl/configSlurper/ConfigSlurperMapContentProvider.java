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
package org.jetbrains.plugins.groovy.impl.configSlurper;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyMapContentProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class ConfigSlurperMapContentProvider extends GroovyMapContentProvider
{

	@Nullable
	private static Pair<ConfigSlurperSupport.PropertiesProvider, List<String>> getInfo(@Nonnull GrExpression qualifier,
																					   @Nullable PsiElement resolve)
	{
		if(!GroovyPsiManager.isInheritorCached(qualifier.getType(), GroovyCommonClassNames.GROOVY_UTIL_CONFIG_OBJECT))
		{
			return null;
		}

		GrExpression resolvedQualifier = qualifier;
		PsiElement resolveResult = resolve;
		List<String> path = new ArrayList<String>();

		while(resolveResult == null)
		{
			if(!(resolvedQualifier instanceof GrReferenceExpression))
			{
				return null;
			}

			GrReferenceExpression expr = (GrReferenceExpression) resolvedQualifier;
			path.add(expr.getReferenceName());

			resolvedQualifier = expr.getQualifierExpression();
			if(resolvedQualifier instanceof GrReferenceExpression)
			{
				resolveResult = ((GrReferenceExpression) resolvedQualifier).resolve();
			}
			else if(resolvedQualifier instanceof GrMethodCall)
			{
				resolveResult = ((GrMethodCall) resolvedQualifier).resolveMethod();
			}
			else
			{
				return null;
			}
		}

		Collections.reverse(path);

		ConfigSlurperSupport.PropertiesProvider propertiesProvider = null;

		for(ConfigSlurperSupport slurperSupport : ConfigSlurperSupport.EP_NAME.getExtensionList())
		{
			propertiesProvider = slurperSupport.getConfigSlurperInfo(resolvedQualifier, resolveResult);
			if(propertiesProvider != null)
			{
				break;
			}
		}

		if(propertiesProvider == null)
		{
			return null;
		}

		return Pair.create(propertiesProvider, path);
	}

	@Override
	protected Collection<String> getKeyVariants(@Nonnull GrExpression qualifier, @Nullable PsiElement resolve)
	{
		Pair<ConfigSlurperSupport.PropertiesProvider, List<String>> info = getInfo(qualifier, resolve);
		if(info == null)
		{
			return Collections.emptyList();
		}

		final Set<String> res = new HashSet<String>();

		info.first.collectVariants(info.second, (variant, isFinal) -> res.add(variant));

		return res;
	}

	@Override
	public PsiType getValueType(@Nonnull GrExpression qualifier, @Nullable PsiElement resolve, @Nonnull final String key)
	{
		Pair<ConfigSlurperSupport.PropertiesProvider, List<String>> info = getInfo(qualifier, resolve);
		if(info == null)
		{
			return null;
		}

		final Ref<Boolean> res = new Ref<Boolean>();

		info.first.collectVariants(info.second, (variant, isFinal) -> {
			if(variant.equals(key))
			{
				res.set(isFinal);
			}
			else if(variant.startsWith(key) && variant.length() > key.length() && variant.charAt(key.length()) == '.')
			{
				res.set(false);
			}
		});

		if(res.get() != null && !res.get())
		{
			return JavaPsiFacade.getElementFactory(qualifier.getProject())
					.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_UTIL_CONFIG_OBJECT, qualifier.getResolveScope());
		}

		return null;
	}
}
