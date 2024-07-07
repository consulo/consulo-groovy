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

package org.jetbrains.plugins.groovy.impl.codeInspection.untypedUnresolvedAccess;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;

import jakarta.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GrUnresolvedAccessInspection extends GroovySuppressableInspectionTool
{
	private static final String SHORT_NAME = "GrUnresolvedAccess";

	public static boolean isSuppressed(@Nonnull PsiElement ref)
	{
		return isElementToolSuppressedIn(ref, SHORT_NAME);
	}

	public static HighlightDisplayKey findDisplayKey()
	{
		return HighlightDisplayKey.find(SHORT_NAME);
	}

	public static GrUnresolvedAccessInspectionState getInstanceState(PsiFile file, Project project)
	{
		return (GrUnresolvedAccessInspectionState) getInspectionProfile(project).getToolState(SHORT_NAME, file);
	}

	public static GrUnresolvedAccessInspection getInstance(PsiFile file, Project project)
	{
		return (GrUnresolvedAccessInspection) getInspectionProfile(project).getUnwrappedTool(SHORT_NAME, file);
	}

	public static boolean isInspectionEnabled(PsiFile file, Project project)
	{
		return getInspectionProfile(project).isToolEnabled(findDisplayKey(), file);
	}

	public static HighlightDisplayLevel getHighlightDisplayLevel(Project project, GrReferenceElement ref)
	{
		return getInspectionProfile(project).getErrorLevel(findDisplayKey(), ref);
	}

	@Nonnull
	private static InspectionProfile getInspectionProfile(@Nonnull Project project)
	{
		return InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
	}

	@Nonnull
	@Override
	public InspectionToolState<?> createStateProvider()
	{
		return new GrUnresolvedAccessInspectionState();
	}

	@Override
	@Nls
	@Nonnull
	public String getGroupDisplayName()
	{
		return BaseInspection.PROBABLE_BUGS;
	}

	@Override
	@Nls
	@Nonnull
	public String getDisplayName()
	{
		return getDisplayText();
	}

	public static String getDisplayText()
	{
		return "Access to unresolved expression";
	}
}
