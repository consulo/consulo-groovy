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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class GrReplacePrimitiveTypeWithWrapperFix extends GroovyFix
{
	private static final Logger LOG = Logger.getInstance(GrReplacePrimitiveTypeWithWrapperFix.class);

	private final String myBoxedName;

	public GrReplacePrimitiveTypeWithWrapperFix(GrTypeElement typeElement)
	{
		LOG.assertTrue(typeElement.isValid());

		final PsiType type = typeElement.getType();
		LOG.assertTrue(type instanceof PsiPrimitiveType);

		myBoxedName = ((PsiPrimitiveType) type).getBoxedType(typeElement).getClassName();
	}

	@Nonnull
	@Override
	public String getName()
	{
		return GroovyIntentionsBundle.message("replace.with.wrapper", myBoxedName);
	}

	@Nonnull
	@Override
	public String getFamilyName()
	{
		return GroovyIntentionsBundle.message("replace.primitive.type.with.wrapper");
	}

	@Override
	protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
	{
		final PsiElement element = descriptor.getPsiElement();
		assert element instanceof GrTypeElement : element;

		GrTypeElement typeElement = (GrTypeElement) element;
		final PsiType type = typeElement.getType();
		if(!(type instanceof PsiPrimitiveType))
			return;

		final PsiClassType boxed = ((PsiPrimitiveType) type).getBoxedType(typeElement);
		final GrTypeElement newTypeElement = GroovyPsiElementFactory.getInstance(project).createTypeElement(boxed);

		final PsiElement replaced = typeElement.replace(newTypeElement);
		JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
	}
}
