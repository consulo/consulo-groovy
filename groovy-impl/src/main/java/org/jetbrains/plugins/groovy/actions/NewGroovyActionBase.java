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

package org.jetbrains.plugins.groovy.actions;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NonNls;
import com.intellij.CommonBundle;
import com.intellij.ide.actions.CreateElementActionBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.ui.image.Image;

public abstract class NewGroovyActionBase extends CreateElementActionBase
{
	@NonNls
	public static final String GROOVY_EXTENSION = ".groovy";

	public NewGroovyActionBase(String text, String description, Image icon)
	{
		super(text, description, icon);
	}

	@Nonnull
	protected final PsiElement[] invokeDialog(final Project project, final PsiDirectory directory)
	{
		MyInputValidator validator = new MyInputValidator(project, directory);
		Messages.showInputDialog(project, getDialogPrompt(), getDialogTitle(), Messages.getQuestionIcon(), "", validator);

		return validator.getCreatedElements();
	}

	protected abstract String getDialogPrompt();

	protected abstract String getDialogTitle();

	@Override
	protected boolean isAvailable(DataContext dataContext)
	{
		if(!super.isAvailable(dataContext))
		{
			return false;
		}

		Module module = dataContext.getData(LangDataKeys.MODULE);
		return module != null && ModuleUtilCore.getExtension(module, GroovyModuleExtension.class) != null;
	}

	@Nonnull
	protected PsiElement[] create(String newName, PsiDirectory directory) throws Exception
	{
		return doCreate(newName, directory);
	}

	@Nonnull
	protected abstract PsiElement[] doCreate(String newName, PsiDirectory directory) throws Exception;


	protected String getErrorTitle()
	{
		return CommonBundle.getErrorTitle();
	}
}
