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

package org.jetbrains.plugins.groovy.editor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.language.editor.CommonDataKeys;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyFileType;
import consulo.language.Language;
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.language.editor.LangDataKeys;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.editor.util.PsiUtilBase;

/**
 * @author ilyas
 */
public class HandlerUtils
{
	private HandlerUtils()
	{
	}

	public static boolean isEnabled(@Nonnull final Editor editor, @Nonnull final DataContext dataContext, @Nullable final EditorActionHandler originalHandler)
	{
		final Project project = getProject(dataContext);
		if(project != null)
		{
			final Language language = PsiUtilBase.getLanguageInEditor(editor, project);
			if(language == GroovyFileType.GROOVY_LANGUAGE)
			{
				return true;
			}
		}

		return originalHandler == null || originalHandler.isEnabled(editor, dataContext);
	}

	public static boolean isReadOnly(@Nonnull final Editor editor)
	{
		if(editor.isViewer())
		{
			return true;
		}
		Document document = editor.getDocument();
		return !document.isWritable();
	}

	public static boolean canBeInvoked(final Editor editor, final Project project)
	{
		if(isReadOnly(editor))
		{
			return false;
		}
		if(getPsiFile(editor, project) == null)
		{
			return false;
		}

		return true;
	}

	public static PsiFile getPsiFile(@Nonnull final Editor editor, @Nonnull final DataContext dataContext)
	{
		return getPsiFile(editor, getProject(dataContext));
	}

	public static PsiFile getPsiFile(@Nonnull final Editor editor, final Project project)
	{
		return PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
	}

	@Nullable
	public static Language getLanguage(@Nonnull final DataContext dataContext)
	{
		return dataContext.getData(LangDataKeys.LANGUAGE);
	}

	@Nullable
	public static Project getProject(@Nonnull final DataContext dataContext)
	{
		return dataContext.getData(CommonDataKeys.PROJECT);
	}
}
