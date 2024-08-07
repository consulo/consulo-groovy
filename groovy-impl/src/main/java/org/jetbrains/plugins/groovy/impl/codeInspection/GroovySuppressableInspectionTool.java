/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.codeInspection;

import com.intellij.java.analysis.impl.codeInspection.JavaSuppressionUtil;
import com.intellij.java.language.psi.PsiAnnotation;
import consulo.application.AccessRule;
import consulo.language.Language;
import consulo.language.editor.inspection.BatchSuppressableTool;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.SuppressQuickFix;
import consulo.language.editor.inspection.SuppressionUtil;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;

/**
 * @author peter
 */
public abstract class GroovySuppressableInspectionTool extends LocalInspectionTool implements BatchSuppressableTool
{
	@Nonnull
	@Override
	public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element)
	{
		return getSuppressActions(getShortName());
	}

	@Nullable
	@Override
	public Language getLanguage()
	{
		return GroovyLanguage.INSTANCE;
	}

	@Nonnull
	@Override
	public HighlightDisplayLevel getDefaultLevel()
	{
		return HighlightDisplayLevel.WARNING;
	}

	public static SuppressQuickFix[] getSuppressActions(String name)
	{
		final HighlightDisplayKey displayKey = HighlightDisplayKey.find(name);
		return new SuppressQuickFix[]{
				new SuppressByGroovyCommentFix(displayKey),
				new SuppressForMemberFix(displayKey, false),
				new SuppressForMemberFix(displayKey, true),
		};
	}

	@Override
	public boolean isSuppressedFor(@Nonnull final PsiElement element)
	{
		return isElementToolSuppressedIn(element, getID());
	}

	public static boolean isElementToolSuppressedIn(final PsiElement place, final String toolId)
	{
		return getElementToolSuppressedIn(place, toolId) != null;
	}

	@Nullable
	public static PsiElement getElementToolSuppressedIn(final PsiElement place, final String toolId)
	{
		if(place == null)
		{
			return null;
		}

		return AccessRule.read(() ->
		{
			final PsiElement statement = PsiUtil.findEnclosingStatement(place);
			if(statement != null)
			{
				PsiElement prev = statement.getPrevSibling();
				while(prev != null && StringUtil.isEmpty(prev.getText().trim()))
				{
					prev = prev.getPrevSibling();
				}
				if(prev instanceof PsiComment)
				{
					String text = prev.getText();
					Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
					if(matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId))
					{
						return prev;
					}
				}
			}

			GrMember member = null;
			GrDocComment docComment = PsiTreeUtil.getParentOfType(place, GrDocComment.class);
			if(docComment != null)
			{
				GrDocCommentOwner owner = docComment.getOwner();
				if(owner instanceof GrMember)
				{
					member = (GrMember) owner;
				}
			}
			if(member == null)
			{
				member = PsiTreeUtil.getNonStrictParentOfType(place, GrMember.class);
			}

			while(member != null)
			{
				GrModifierList modifierList = member.getModifierList();
				for(String ids : getInspectionIdsSuppressedInAnnotation(modifierList))
				{
					if(SuppressionUtil.isInspectionToolIdMentioned(ids, toolId))
					{
						return modifierList;
					}
				}

				member = PsiTreeUtil.getParentOfType(member, GrMember.class);
			}

			return null;
		});
	}

	@Nonnull
	private static Collection<String> getInspectionIdsSuppressedInAnnotation(final GrModifierList modifierList)
	{
		if(modifierList == null)
		{
			return Collections.emptyList();
		}
		PsiAnnotation annotation = modifierList.findAnnotation(JavaSuppressionUtil.SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
		if(annotation == null)
		{
			return Collections.emptyList();
		}
		final GrAnnotationMemberValue attributeValue = (GrAnnotationMemberValue) annotation.findAttributeValue(null);
		Collection<String> result = new ArrayList<String>();
		if(attributeValue instanceof GrAnnotationArrayInitializer)
		{
			for(GrAnnotationMemberValue annotationMemberValue : ((GrAnnotationArrayInitializer) attributeValue).getInitializers())
			{
				final String id = getInspectionIdSuppressedInAnnotationAttribute(annotationMemberValue);
				if(id != null)
				{
					result.add(id);
				}
			}
		}
		else
		{
			final String id = getInspectionIdSuppressedInAnnotationAttribute(attributeValue);
			if(id != null)
			{
				result.add(id);
			}
		}
		return result;
	}

	@Nullable
	private static String getInspectionIdSuppressedInAnnotationAttribute(GrAnnotationMemberValue element)
	{
		if(element instanceof GrLiteral)
		{
			final Object value = ((GrLiteral) element).getValue();
			if(value instanceof String)
			{
				return (String) value;
			}
		}
		return null;
	}


}
