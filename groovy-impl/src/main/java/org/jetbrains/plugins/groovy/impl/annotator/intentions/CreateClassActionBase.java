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

import com.intellij.java.impl.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplatesFactory;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public abstract class CreateClassActionBase extends Intention implements SyntheticIntentionAction
{
	private final GrCreateClassKind myType;

	protected final GrReferenceElement myRefElement;
	private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions" +
			".CreateClassActionBase");

	public CreateClassActionBase(GrCreateClassKind type, GrReferenceElement refElement)
	{
		myType = type;
		myRefElement = refElement;
	}

	@Override
	@Nonnull
	public String getText()
	{
		String referenceName = myRefElement.getReferenceName();
		switch(getType())
		{
			case TRAIT:
				return GroovyBundle.message("create.trait", referenceName);
			case ENUM:
				return GroovyBundle.message("create.enum", referenceName);
			case CLASS:
				return GroovyBundle.message("create.class.text", referenceName);
			case INTERFACE:
				return GroovyBundle.message("create.interface.text", referenceName);
			case ANNOTATION:
				return GroovyBundle.message("create.annotation.text", referenceName);
			default:
				return "";
		}
	}

	@Override
	@Nonnull
	public String getFamilyName()
	{
		return GroovyBundle.message("create.class.family.name");
	}

	@Override
	public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file)
	{
		return myRefElement.isValid() && ModuleUtilCore.findModuleForPsiElement(myRefElement) != null;
	}

	@Override
	public boolean startInWriteAction()
	{
		return false;
	}


	protected GrCreateClassKind getType()
	{
		return myType;
	}

	@Nullable
	public static GrTypeDefinition createClassByType(@Nonnull final PsiDirectory directory,
			@Nonnull final String name,
			@Nonnull final PsiManager manager,
			@Nullable final PsiElement contextElement,
			@Nonnull final String templateName,
			boolean allowReformatting)
	{
		AccessToken accessToken = WriteAction.start();

		try
		{
			GrTypeDefinition targetClass = null;
			try
			{
				PsiFile file = GroovyTemplatesFactory.createFromTemplate(directory, name, name + ".groovy",
						templateName, allowReformatting);
				for(PsiElement element : file.getChildren())
				{
					if(element instanceof GrTypeDefinition)
					{
						targetClass = ((GrTypeDefinition) element);
						break;
					}
				}
				if(targetClass == null)
				{
					throw new IncorrectOperationException(GroovyBundle.message("no.class.in.file.template"));
				}
			}
			catch(final IncorrectOperationException e)
			{
				ApplicationManager.getApplication().invokeLater(new Runnable()
				{
					@Override
					public void run()
					{
						Messages.showErrorDialog(GroovyBundle.message("cannot.create.class.error.text", name,
								e.getLocalizedMessage()), GroovyBundle.message("cannot.create.class.error.title"));
					}
				});
				return null;
			}
			PsiModifierList modifiers = targetClass.getModifierList();
			if(contextElement != null &&
					!JavaPsiFacade.getInstance(manager.getProject()).getResolveHelper().isAccessible(targetClass,
																																													 contextElement, null) &&
					modifiers != null)
			{
				modifiers.setModifierProperty(PsiModifier.PUBLIC, true);
			}
			return targetClass;
		}
		catch(IncorrectOperationException e)
		{
			LOG.error(e);
			return null;
		}
		finally
		{
			accessToken.finish();
		}
	}

	@Nullable
	protected PsiDirectory getTargetDirectory(@Nonnull Project project,
			@Nonnull String qualifier,
			@Nonnull String name,
			@Nullable Module module,
			@Nonnull String title)
	{
		CreateClassDialog dialog = new CreateClassDialog(project, title, name, qualifier, getType(), false, module)
		{
			@Override
			protected boolean reportBaseInSourceSelectionInTest()
			{
				return true;
			}
		};
		dialog.show();
		if(dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE)
		{
			return null;
		}

		return dialog.getTargetDirectory();
	}

	@Nonnull
	@Override
	protected PsiElementPredicate getElementPredicate()
	{
		return new PsiElementPredicate()
		{
			@Override
			public boolean satisfiedBy(PsiElement element)
			{
				return myRefElement.isValid();
			}
		};
	}
}
