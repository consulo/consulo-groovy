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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.java.language.impl.psi.impl.source.tree.java.PsiLocalVariableImpl;
import com.intellij.java.language.impl.psi.scope.ElementClassHint;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.StaticChecker;

import jakarta.annotation.Nonnull;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

/**
 * @author ven
 */
public class ResolverProcessorImpl implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint
{
	protected String myName;
	private final EnumSet<ResolveKind> myResolveTargetKinds;
	private Set<String> myProcessedClasses;
	protected PsiElement myPlace;
	private
	@Nonnull
	final PsiType[] myTypeArguments;

	private List<GroovyResolveResult> myCandidates;

	protected ResolverProcessorImpl(@Nullable String name, @Nonnull EnumSet<ResolveKind> resolveTargets, @Nonnull PsiElement place, @Nonnull PsiType[] typeArguments)
	{
		myName = name;
		myResolveTargetKinds = resolveTargets;
		myPlace = place;
		myTypeArguments = typeArguments;
	}

	public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state)
	{
		if(element instanceof PsiLocalVariableImpl)
		{ //todo a better hack
			return true; // the debugger creates a Java code block context and our expressions to evaluate resolve there
		}

		if(myResolveTargetKinds.contains(getResolveKind(element)))
		{
			//hack for resolve of java local vars and parameters
			//don't check field for name because they can be aliased imported
			if(element instanceof PsiVariable && !(element instanceof PsiField) &&
					myName != null && !myName.equals(((PsiVariable) element).getName()))
			{
				return true;
			}
			PsiNamedElement namedElement = (PsiNamedElement) element;
			PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
			if(substitutor == null)
			{
				substitutor = PsiSubstitutor.EMPTY;
			}

			if(myTypeArguments.length > 0 && namedElement instanceof PsiClass)
			{
				substitutor = substitutor.putAll((PsiClass) namedElement, myTypeArguments);
			}

			if(namedElement instanceof PsiClass && !(namedElement instanceof PsiTypeParameter))
			{
				final PsiClass aClass = (PsiClass) namedElement;
				if(myProcessedClasses == null)
				{
					myProcessedClasses = new HashSet<String>();
				}
				if(!myProcessedClasses.add(aClass.getQualifiedName()))
				{
					return true;
				}
			}

			boolean isAccessible = isAccessible(namedElement);
			final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
			final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
			boolean isStaticsOK = isStaticsOK(namedElement, resolveContext, true);
			addCandidate(new GroovyResolveResultImpl(namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK));
			return !(isAccessible && isStaticsOK);
		}

		return true;
	}

	protected final void addCandidate(@Nonnull GroovyResolveResult candidate)
	{
		PsiElement element = candidate.getElement();
		assert element == null || element.isValid() : getElementInfo(element);

		if(myCandidates == null)
		{
			myCandidates = new ArrayList<GroovyResolveResult>();
		}
		myCandidates.add(candidate);
	}

	@Nonnull
	private static String getElementInfo(@Nonnull PsiElement element)
	{
		String text;
		if(element instanceof LightElement)
		{
			final PsiElement context = element.getContext();
			text = context != null ? context.getText() : null;
		}
		else
		{
			text = element.getText();
		}
		return "invalid resolve candidate: " + element.getClass() + ", text: " + text;
	}

	@Nonnull
	protected List<GroovyResolveResult> getCandidatesInternal()
	{
		return myCandidates == null ? Collections.<GroovyResolveResult>emptyList() : myCandidates;
	}

	protected boolean isAccessible(@Nonnull PsiNamedElement namedElement)
	{
		if(namedElement instanceof GrField)
		{
			final GrField field = (GrField) namedElement;
			if(PsiUtil.isAccessible(myPlace, field))
			{
				return true;
			}

			for(GrAccessorMethod method : field.getGetters())
			{
				if(PsiUtil.isAccessible(myPlace, method))
				{
					return true;
				}
			}
			final GrAccessorMethod setter = field.getSetter();
			if(setter != null && PsiUtil.isAccessible(myPlace, setter))
			{
				return true;
			}

			return false;
		}

		return !(namedElement instanceof PsiMember) ||
				PsiUtil.isAccessible(myPlace, ((PsiMember) namedElement));
	}

	protected boolean isStaticsOK(@Nonnull PsiNamedElement element, @Nullable PsiElement resolveContext, boolean filterStaticAfterInstanceQualifier)
	{
		if(resolveContext instanceof GrImportStatement)
		{
			return true;
		}

		if(element instanceof PsiModifierListOwner)
		{
			return StaticChecker.isStaticsOK((PsiModifierListOwner) element, myPlace, resolveContext, filterStaticAfterInstanceQualifier);
		}
		return true;
	}

	@Nonnull
	public GroovyResolveResult[] getCandidates()
	{
		if(myCandidates == null)
		{
			return GroovyResolveResult.EMPTY_ARRAY;
		}
		return myCandidates.toArray(new GroovyResolveResult[myCandidates.size()]);
	}

	@SuppressWarnings({"unchecked"})
	public <T> T getHint(@Nonnull Key<T> hintKey)
	{
		if((NameHint.KEY == hintKey && myName != null) || ClassHint.KEY == hintKey || ElementClassHint.KEY == hintKey)
		{
			return (T) this;
		}

		return null;
	}

	public void handleEvent(Event event, Object associated)
	{
	}

	public String getName()
	{
		return myName;
	}

	public boolean shouldProcess(ResolveKind resolveKind)
	{
		return myResolveTargetKinds.contains(resolveKind);
	}

	public boolean shouldProcess(DeclarationKind kind)
	{
		switch(kind)
		{
			case CLASS:
				return shouldProcess(CLASS);

			case ENUM_CONST:
			case VARIABLE:
			case FIELD:
				return shouldProcess(PROPERTY);

			case METHOD:
				return shouldProcess(METHOD);

			case PACKAGE:
				return shouldProcess(PACKAGE);
		}

		return false;
	}

	public boolean hasCandidates()
	{
		return myCandidates != null;
	}

	@Nullable
	private static ResolveKind getResolveKind(PsiElement element)
	{
		if(element instanceof PsiVariable)
		{
			return PROPERTY;
		}
		if(element instanceof PsiMethod)
		{
			return METHOD;
		}
		if(element instanceof PsiJavaPackage)
		{
			return PACKAGE;
		}
		if(element instanceof PsiClass)
		{
			return CLASS;
		}
		return null;
	}

	public String getName(ResolveState state)
	{
		return myName;
	}

	@Override
	public String toString()
	{
		return "NameHint: '" +
				myName +
				"', " +
				myResolveTargetKinds.toString() +
				", Candidates: " +
				(myCandidates == null ? 0 : myCandidates.size());
	}
}
