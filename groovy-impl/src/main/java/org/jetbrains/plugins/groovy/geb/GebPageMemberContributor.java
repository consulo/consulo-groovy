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
package org.jetbrains.plugins.groovy.geb;

import java.util.Map;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;

/**
 * @author Sergey Evdokimov
 */
public class GebPageMemberContributor extends NonCodeMembersContributor
{

	@Override
	protected String getParentClassName()
	{
		return "geb.Page";
	}

	@Override
	public void processDynamicElements(@Nonnull PsiType qualifierType,
			PsiClass aClass,
			@Nonnull PsiScopeProcessor processor,
			@Nonnull PsiElement place,
			@Nonnull ResolveState state)
	{
		ClassHint classHint = processor.getHint(ClassHint.KEY);
		if(classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY))
		{
			return;
		}

		PsiElement grCall = place.getParent();
		if(grCall instanceof GrMethodCall)
		{
			PsiElement grClosure = grCall.getParent();
			if(grClosure instanceof GrClosableBlock)
			{
				PsiElement contentField = grClosure.getParent();
				if(contentField instanceof GrField)
				{
					GrField f = (GrField) contentField;
					if("content".equals(f.getName()) && f.hasModifierProperty(PsiModifier.STATIC) && f
							.getContainingClass() == aClass)
					{
						Map<String, PsiField> elements = GebUtil.getContentElements(aClass);
						for(PsiField field : elements.values())
						{
							if(field.getNavigationElement() == place)
							{
								return; // Don't resolve variable definition.
							}
						}
					}
				}
			}
		}

		processPageFields(processor, aClass, state);
	}

	public static boolean processPageFields(PsiScopeProcessor processor,
			@Nonnull PsiClass pageClass,
			ResolveState state)
	{
		Map<String, PsiClass> supers = ClassUtil.getSuperClassesWithCache(pageClass);
		String nameHint = ResolveUtil.getNameHint(processor);

		for(PsiClass psiClass : supers.values())
		{
			Map<String, PsiField> contentFields = GebUtil.getContentElements(psiClass);

			if(nameHint == null)
			{
				for(Map.Entry<String, PsiField> entry : contentFields.entrySet())
				{
					if(!processor.execute(entry.getValue(), state))
					{
						return false;
					}
				}
			}
			else
			{
				PsiField field = contentFields.get(nameHint);
				if(field != null)
				{
					return processor.execute(field, state);
				}
			}
		}

		return true;
	}
}
