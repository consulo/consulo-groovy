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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public abstract class CustomAnnotationChecker
{
	public static final ExtensionPointName<CustomAnnotationChecker> EP_NAME = ExtensionPointName.create("org.intellij" +
			".groovy.customAnnotationChecker");

	public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation)
	{
		return false;
	}

	public boolean checkApplicability(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation)
	{
		return false;
	}

	@Nullable
	public static String isAnnotationApplicable(@Nonnull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner)
	{
		if(!(owner instanceof PsiElement))
		{
			return null;
		}
		PsiElement ownerToUse = owner instanceof PsiModifierList ? ((PsiElement) owner).getParent() : (PsiElement)
				owner;
		PsiAnnotation.TargetType[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
		if(elementTypeFields.length != 0 && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, elementTypeFields))
		{
			String annotationTargetText = JavaErrorBundle.message("annotation.target." + elementTypeFields[0]);
			GrCodeReferenceElement ref = annotation.getClassReference();
			return JavaErrorBundle.message("annotation.not.applicable", ref.getText(), annotationTargetText);
		}

		return null;
	}

	public static Pair<PsiElement, String> checkAnnotationArguments(@Nonnull PsiClass annotation,
																	@Nonnull GrAnnotationNameValuePair[] attributes,
																	boolean checkMissedAttributes)
	{
		Set<String> usedAttrs = new HashSet<>();

		if(attributes.length > 0)
		{
			final PsiElement identifier = attributes[0].getNameIdentifierGroovy();
			if(attributes.length == 1 && identifier == null)
			{
				Pair.NonNull<PsiElement, String> r =
						checkAnnotationValue(annotation, attributes[0], "value", usedAttrs, attributes[0].getValue());
				if(r != null)
					return r;
			}
			else
			{
				for(GrAnnotationNameValuePair attribute : attributes)
				{
					final String name = attribute.getName();
					if(name != null)
					{
						final PsiElement toHighlight = attribute.getNameIdentifierGroovy();
						assert toHighlight != null;
						Pair.NonNull<PsiElement, String> r = checkAnnotationValue(annotation, toHighlight, name, usedAttrs, attribute.getValue());
						if(r != null)
							return r;
					}
				}
			}
		}

		List<String> missedAttrs = new ArrayList<>();
		final PsiMethod[] methods = annotation.getMethods();
		for(PsiMethod method : methods)
		{
			final String name = method.getName();
			if(usedAttrs.contains(name) ||
					method instanceof PsiAnnotationMethod && ((PsiAnnotationMethod) method).getDefaultValue() != null)
			{
				continue;
			}
			missedAttrs.add(name);
		}

		if(checkMissedAttributes && !missedAttrs.isEmpty())
		{
			return Pair.create(null, GroovyBundle.message("missed.attributes", StringUtil.join(missedAttrs, ", ")));
		}
		return null;
	}

	private static Pair.NonNull<PsiElement, String> checkAnnotationValue(@Nonnull PsiClass annotation,
																		 @Nonnull PsiElement identifierToHighlight,
																		 @Nonnull String name,
																		 @Nonnull Set<? super String> usedAttrs,
																		 @Nullable GrAnnotationMemberValue value)
	{
		if(!usedAttrs.add(name))
		{
			return Pair.createNonNull(identifierToHighlight, GroovyBundle.message("duplicate.attribute"));
		}

		final PsiMethod[] methods = annotation.findMethodsByName(name, false);
		if(methods.length == 0)
		{
			return Pair.createNonNull(identifierToHighlight,
					GroovyBundle.message("at.interface.0.does.not.contain.attribute", annotation.getQualifiedName(), name));
		}
		final PsiMethod method = methods[0];
		final PsiType ltype = method.getReturnType();
		if(ltype != null && value != null)
		{
			return checkAnnotationValueByType(value, ltype, true);
		}
		return null;
	}

	public static Pair.NonNull<PsiElement, String> checkAnnotationValueByType(@Nonnull GrAnnotationMemberValue value,
																								 @Nullable PsiType ltype,
																								 boolean skipArrays)
	{
		final GlobalSearchScope resolveScope = value.getResolveScope();
		final PsiManager manager = value.getManager();

		if(value instanceof GrExpression)
		{
			final PsiType rtype;
			if(value instanceof GrFunctionalExpression)
			{
				rtype = PsiType.getJavaLangClass(manager, resolveScope);
			}
			else
			{
				rtype = ((GrExpression) value).getType();
			}

			if(rtype != null && !isAnnoTypeAssignable(ltype, rtype, value, skipArrays))
			{
				return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
			}
		}

		else if(value instanceof GrAnnotation)
		{
			final PsiElement resolved = ((GrAnnotation) value).getClassReference().resolve();
			if(resolved instanceof PsiClass)
			{
				final PsiClassType rtype = JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass) resolved, PsiSubstitutor.EMPTY);
				if(!isAnnoTypeAssignable(ltype, rtype, value, skipArrays))
				{
					return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
				}
			}
		}

		else if(value instanceof GrAnnotationArrayInitializer)
		{
			if(ltype instanceof PsiArrayType)
			{
				final PsiType componentType = ((PsiArrayType) ltype).getComponentType();
				final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer) value).getInitializers();
				for(GrAnnotationMemberValue initializer : initializers)
				{
					Pair.NonNull<PsiElement, String> r = checkAnnotationValueByType(initializer, componentType, false);
					if(r != null)
						return r;
				}
			}
			else
			{
				final PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer) value);
				if(!isAnnoTypeAssignable(ltype, rtype, value, skipArrays))
				{
					return Pair.createNonNull(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
				}
			}
		}
		return null;
	}

	private static boolean isAnnoTypeAssignable(@Nullable PsiType type,
												@Nullable PsiType rtype,
												@Nonnull GroovyPsiElement context,
												boolean skipArrays)
	{
		rtype = TypesUtil.unboxPrimitiveTypeWrapper(rtype);
		if(TypesUtil.isAssignableByMethodCallConversion(type, rtype, context))
		{
			return true;
		}

		if(!(type instanceof PsiArrayType && skipArrays))
		{
			return false;
		}

		final PsiType componentType = ((PsiArrayType) type).getComponentType();
		return isAnnoTypeAssignable(componentType, rtype, context, skipArrays);
	}
}
