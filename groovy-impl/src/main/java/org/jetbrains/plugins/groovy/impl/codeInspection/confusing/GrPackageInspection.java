/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SyntheticElement;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.GrMoveToDirFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrPackageInspection extends BaseInspection<GrPackageInspectionState>
{
	@Nls
	@Nonnull
	public String getGroupDisplayName()
	{
		return CONFUSING_CODE_CONSTRUCTS;
	}

	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return "Package name mismatch";
	}

	@Nonnull
	@Override
	public InspectionToolState<GrPackageInspectionState> createStateProvider()
	{
		return new GrPackageInspectionState();
	}

	@Nullable
	protected String buildErrorString(Object... args)
	{
		return "Package name mismatch";
	}

	@Nonnull
	@Override
	protected BaseInspectionVisitor<GrPackageInspectionState> buildVisitor()
	{
		return new BaseInspectionVisitor<>()
		{
			@Override
			public void visitFile(GroovyFileBase file)
			{
				if(!(file instanceof GroovyFile))
				{
					return;
				}

				if(!myState.myCheckScripts && file.isScript())
				{
					return;
				}

				String expectedPackage = ResolveUtil.inferExpectedPackageName(file);
				String actual = file.getPackageName();
				if(!expectedPackage.equals(actual))
				{

					PsiElement toHighlight = getElementToHighlight((GroovyFile) file);
					if(toHighlight == null)
					{
						return;
					}

					registerError(toHighlight, "Package name mismatch. Actual: '" + actual + "', expected: '" + expectedPackage + "'",
							new LocalQuickFix[]{
									new ChangePackageQuickFix(expectedPackage),
									new GrMoveToDirFix(actual)
							},
							ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
				}
			}
		};
	}

	@Nullable
	private static PsiElement getElementToHighlight(GroovyFile file)
	{
		GrPackageDefinition packageDefinition = file.getPackageDefinition();
		if(packageDefinition != null)
		{
			return packageDefinition;
		}

		PsiClass[] classes = file.getClasses();
		for(PsiClass aClass : classes)
		{
			if(!(aClass instanceof SyntheticElement) && aClass instanceof GrTypeDefinition)
			{
				return ((GrTypeDefinition) aClass).getNameIdentifierGroovy();
			}
		}

		GrTopStatement[] statements = file.getTopStatements();
		if(statements.length > 0)
		{
			GrTopStatement first = statements[0];
			if(first instanceof GrNamedElement)
			{
				return ((GrNamedElement) first).getNameIdentifierGroovy();
			}

			return first;
		}

		return null;
	}

	/**
	 * User: Dmitry.Krasilschikov
	 * Date: 01.11.2007
	 */
	public static class ChangePackageQuickFix implements LocalQuickFix
	{
		private final String myNewPackageName;

		public ChangePackageQuickFix(String newPackageName)
		{
			myNewPackageName = newPackageName;
		}

		@Nonnull
		@Override
		public String getName()
		{
			return GroovyBundle.message("fix.package.name");
		}

		@Nonnull
		public String getFamilyName()
		{
			return GroovyBundle.message("fix.package.name");
		}

		@Override
		public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor)
		{
			PsiFile file = descriptor.getPsiElement().getContainingFile();
			((GroovyFile) file).setPackageName(myNewPackageName);
		}
	}
}
