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
package org.jetbrains.plugins.groovy.actions;

import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiFile;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.language.editor.PlatformDataKeys;
import consulo.codeEditor.Editor;
import consulo.application.dumb.DumbAware;
import consulo.language.psi.PsiFile;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import org.jetbrains.plugins.groovy.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator;

import javax.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class DumpGroovyStubsAction extends AnAction implements DumbAware
{
	@RequiredUIAccess
	@Override
	public void update(@Nonnull AnActionEvent e)
	{
		Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if(editor != null)
		{
			PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
			if(psiFile instanceof GroovyFile)
			{
				e.getPresentation().setEnabled(true);
				return;
			}
		}
		e.getPresentation().setEnabled(false);
	}

	@RequiredUIAccess
	@Override
	public void actionPerformed(@Nonnull AnActionEvent e)
	{
		final Editor editor = e.getData(PlatformDataKeys.EDITOR);
		if(editor == null)
		{
			return;
		}

		final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
		if(!(psiFile instanceof GroovyFile))
		{
			return;
		}

		final StringBuilder builder = GroovyToJavaGenerator.generateStubs(psiFile);
		System.out.println(builder.toString());
	}
}
