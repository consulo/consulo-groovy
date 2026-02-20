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
package org.jetbrains.plugins.groovy.impl.annotator.checkers;

import com.intellij.java.language.psi.PsiAnnotationOwner;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.annotator.GrRemoveAnnotationIntention;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 25/03/14
 */
public class AnnotationChecker
{
	public static void checkApplicability(@Nonnull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner, @Nonnull AnnotationHolder holder, @Nonnull PsiElement toHighlight)
	{
		GrCodeReferenceElement ref = annotation.getClassReference();
		PsiElement resolved = ref.resolve();

		if(resolved == null)
		{
			return;
		}
		assert resolved instanceof PsiClass;

		PsiClass anno = (PsiClass) resolved;
		String qname = anno.getQualifiedName();
		if(!anno.isAnnotationType() && GrAnnotationCollector.findAnnotationCollector(anno) == null)
		{
			if(qname != null)
			{
				holder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.annotation", qname));
			}
			return;
		}

		for(CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensionList())
		{
			if(checker.checkApplicability(holder, annotation))
			{
				return;
			}
		}

		String description = CustomAnnotationChecker.isAnnotationApplicable(annotation, owner);
		if(description != null)
		{
			holder.createErrorAnnotation(ref, description).registerFix(new GrRemoveAnnotationIntention());
		}
	}

	@Nullable
	public static Pair<PsiElement, String> checkAnnotationArgumentList(@Nonnull GrAnnotation annotation,
																	   @Nonnull AnnotationHolder holder)
	{
		PsiClass anno = ResolveUtil.resolveAnnotation(annotation);
		if(anno == null)
		{
			return null;
		}

		for(CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensionList())
		{
			if(checker.checkArgumentList(holder, annotation))
			{
				return Pair.create(null, null);
			}
		}

		return CustomAnnotationChecker.checkAnnotationArguments(anno, annotation.getParameterList().getAttributes(), true);
	}
}
