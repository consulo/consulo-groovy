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

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.ResolveState;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class AccessorResolverProcessor extends MethodResolverProcessor
{
	private final String myPropertyName;
	private final boolean mySearchForGetter;
	private final SubstitutorComputer mySubstitutorComputer;


	public AccessorResolverProcessor(@Nullable String accessorName,
			@Nonnull String propertyName,
			@Nonnull PsiElement place,
			boolean searchForGetter)
	{
		this(accessorName, propertyName, place, searchForGetter, false, null, PsiType.EMPTY_ARRAY);
	}

	public AccessorResolverProcessor(@Nullable String accessorName,
			@Nonnull String propertyName,
			@Nonnull PsiElement place,
			boolean searchForGetter,
			boolean byShape,
			@Nullable PsiType thisType,
			@Nonnull PsiType[] typeArguments)
	{
		super(accessorName, place, false, thisType, null, typeArguments, false, byShape);
		myPropertyName = propertyName;

		mySearchForGetter = searchForGetter;
		mySubstitutorComputer = byShape ? null : new SubstitutorComputer(thisType, PsiType.EMPTY_ARRAY, typeArguments,
				place, myPlace);
	}

	@Override
	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		final PsiElement resolveContext = state.get(ResolverProcessorImpl.RESOLVE_CONTEXT);
		String importedName = resolveContext instanceof GrImportStatement ? ((GrImportStatement) resolveContext)
				.getImportedName() : null;
		if(mySearchForGetter)
		{
			if(element instanceof PsiMethod && (importedName != null && GroovyPropertyUtils.isSimplePropertyGetter(
					(PsiMethod) element, null) &&
					(isAppropriatePropertyNameForGetter((PsiMethod) element, importedName,
							myPropertyName) || myPropertyName.equals(importedName)) || importedName == null &&
					GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod) element, myPropertyName)))
			{
				return addAccessor((PsiMethod) element, state);
			}
		}
		else
		{
			if(element instanceof PsiMethod && (importedName != null && GroovyPropertyUtils.isSimplePropertySetter(
					(PsiMethod) element, null) &&
					(isAppropriatePropertyNameForSetter(importedName, myPropertyName) || myPropertyName.equals
							(importedName)) || importedName == null && GroovyPropertyUtils.isSimplePropertySetter(
					(PsiMethod) element, myPropertyName)))
			{
				return addAccessor((PsiMethod) element, state);
			}
		}
		return true;
	}

	/**
	 * use only for imported properties
	 */
	private static boolean isAppropriatePropertyNameForSetter(@Nonnull String importedName,
			@Nonnull String propertyName)
	{
		propertyName = GroovyPropertyUtils.decapitalize(propertyName);
		return propertyName.equals(GroovyPropertyUtils.getPropertyNameBySetterName(importedName));
	}

	/**
	 * use only for imported properties
	 */
	private static boolean isAppropriatePropertyNameForGetter(@Nonnull PsiMethod getter,
			@Nonnull String importedNameForGetter,
			@Nonnull String propertyName)
	{
		propertyName = GroovyPropertyUtils.decapitalize(propertyName);
		return propertyName.equals(getPropertyNameByGetter(getter, importedNameForGetter));
	}

	@Nullable
	private static String getPropertyNameByGetter(PsiMethod element, String importedName)
	{
		return GroovyPropertyUtils.getPropertyNameByGetterName(importedName, isBoolean(element));
	}

	private static boolean isBoolean(PsiMethod method)
	{
		return method.getReturnType() == PsiType.BOOLEAN;
	}

	private boolean addAccessor(PsiMethod method, ResolveState state)
	{
		PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
		if(substitutor == null)
		{
			substitutor = PsiSubstitutor.EMPTY;
		}

		if(mySubstitutorComputer != null)
		{
			substitutor = mySubstitutorComputer.obtainSubstitutor(substitutor, method, state);
		}
		boolean isAccessible = isAccessible(method);
		final PsiElement resolveContext = state.get(ResolverProcessorImpl.RESOLVE_CONTEXT);
		final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
		boolean isStaticsOK = isStaticsOK(method, resolveContext, false);
		final GroovyResolveResultImpl candidate = new GroovyResolveResultImpl(method, resolveContext, spreadState,
				substitutor, isAccessible, isStaticsOK, true, true);
		if(isAccessible && isStaticsOK)
		{
			addCandidate(candidate);
			return method instanceof GrGdkMethod; //don't stop searching if we found only gdk method
		}
		else
		{
			addInapplicableCandidate(candidate);
			return true;
		}
	}

	@Nonnull
	@Override
	public GroovyResolveResult[] getCandidates()
	{
		final boolean hasApplicableCandidates = hasApplicableCandidates();
		final GroovyResolveResult[] candidates = super.getCandidates();
		if(hasApplicableCandidates)
		{
			if(candidates.length <= 1)
			{
				return candidates;
			}
			return new GroovyResolveResult[]{candidates[0]};
		}
		else
		{
			return candidates;
		}
	}
}
