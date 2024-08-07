/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.navigation.ItemPresentation;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.AbstractPsiBasedNode;
import consulo.navigation.NavigationItem;
import consulo.module.Module;
import consulo.application.util.Queryable;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.editor.wolfAnalyzer.WolfTheProblemSolver;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Dmitry Krasilschikov
 */
public abstract class AbstractMvcPsiNodeDescriptor extends AbstractPsiBasedNode<PsiElement>
{
	public static final int FOLDER = 100;
	public static final int FILE = 110;
	public static final int CLASS = 5;
	public static final int FIELD = 7;
	public static final int METHOD = 10;
	public static final int DOMAIN_CLASSES_FOLDER = 20;
	public static final int CONTROLLERS_FOLDER = 30;
	public static final int VIEWS_FOLDER = 40;
	public static final int SERVICES_FOLDER = 50;
	public static final int CONFIG_FOLDER = 60;
	public static final int OTHER_GRAILS_APP_FOLDER = 64;
	public static final int WEB_APP_FOLDER = 65;
	public static final int SRC_FOLDERS = 70;
	public static final int TESTS_FOLDER = 80;
	public static final int TAGLIB_FOLDER = 90;

	private final Module myModule;
	private final int myWeight;

	protected AbstractMvcPsiNodeDescriptor(@Nonnull final Module module,
										   @Nullable final ViewSettings viewSettings,
										   @Nonnull final PsiElement nodeId, int weight)
	{
		super(module.getProject(), nodeId, viewSettings);
		myModule = module;
		myWeight = weight;
	}

	@NonNls
	protected abstract String getTestPresentationImpl(@Nonnull final PsiElement psiElement);

	@Override
	public final boolean contains(@Nonnull final VirtualFile file)
	{
		return isValid() && containsImpl(file);
	}

	protected boolean containsImpl(@Nonnull final VirtualFile file)
	{
		return super.contains(file);
	}

	@Override
	@Nullable
	protected PsiElement extractPsiFromValue()
	{
		return getValue();
	}

	@Nullable
	@Override
	public String toTestString(@Nullable Queryable.PrintInfo printInfo)
	{
		final PsiElement psi = extractPsiFromValue();
		if(psi == null || !psi.isValid() || !isValid())
		{
			return "null";
		}

		return getTestPresentationImpl(psi);
	}

	@Nonnull
	public Module getModule()
	{
		return myModule;
	}

	@Nullable
	@Override
	public VirtualFile getVirtualFile()
	{
		if(!isValid())
		{
			return null;
		}
		return PsiUtilCore.getVirtualFile(extractPsiFromValue());
	}

	@Override
	protected void updateImpl(final PresentationData data)
	{
		final PsiElement psiElement = extractPsiFromValue();
		if(psiElement instanceof NavigationItem)
		{
			final ItemPresentation presentation = ((NavigationItem) psiElement).getPresentation();
			assert presentation != null;

			data.setPresentableText(presentation.getPresentableText());
		}
	}

	@Override
	public final int getTypeSortWeight(final boolean sortByType)
	{
		return myWeight;
	}

	@Override
	protected boolean hasProblemFileBeneath()
	{
		return WolfTheProblemSolver.getInstance(getProject()).hasProblemFilesBeneath(new Condition<VirtualFile>()
		{
			@Override
			public boolean value(final VirtualFile virtualFile)
			{
				return contains(virtualFile);
			}
		});
	}

	@Override
	public boolean isValid()
	{
		final PsiElement psiElement = extractPsiFromValue();
		return psiElement != null && psiElement.isValid();
	}
}
