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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.impl.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

public class ReplaceWithImportFix extends GroovyFix
{
	private static final Logger LOG = Logger.getInstance(UnnecessaryQualifiedReferenceInspection.class);

	@Override
	protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
	{
		final PsiElement startElement = descriptor.getStartElement();
		LOG.assertTrue(startElement instanceof GrReferenceElement<?>);
		GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>) startElement);
	}

	@Nonnull
	@Override
	public String getName()
	{
		return GroovyInspectionBundle.message("replace.qualified.name.with.import");
	}
}
