/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.util;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.application.AllIcons;
import consulo.content.bundle.Sdk;
import consulo.dataContext.DataContext;
import consulo.ide.impl.idea.ide.util.PropertiesComponent;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModulesAlphaComparator;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.ex.popup.PopupStep;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class ModuleChooserUtil
{

	private static final String GROOVY_LAST_MODULE = "Groovy.Last.Module.Chosen";

	public static void selectModule(@Nonnull Project project, final Collection<Module> suitableModules, final Function<Module, String> versionProvider, final Consumer<Module> callback)
	{
		selectModule(project, suitableModules, versionProvider, callback, null);
	}

	public static void selectModule(@Nonnull Project project,
			final Collection<Module> suitableModules,
			final Function<Module, String> versionProvider,
			final Consumer<Module> callback,
			@Nullable DataContext context)
	{
		final List<Module> modules = new ArrayList<>();
		final Map<Module, String> versions = new HashMap<>();

		for(Module module : suitableModules)
		{
			modules.add(module);
			versions.put(module, versionProvider.apply(module));
		}

		if(modules.size() == 1)
		{
			callback.accept(modules.get(0));
			return;
		}

		Collections.sort(modules, ModulesAlphaComparator.INSTANCE);

		BaseListPopupStep<Module> step = new BaseListPopupStep<Module>("Which module to use classpath of?", modules, AllIcons.Nodes.Module)
		{
			@Nonnull
			@Override
			public String getTextFor(Module value)
			{
				return String.format("%s (%s)", value.getName(), versions.get(value));
			}

			@Override
			public String getIndexedString(Module value)
			{
				return value.getName();
			}

			@Override
			public boolean isSpeedSearchEnabled()
			{
				return true;
			}

			@Override
			public PopupStep onChosen(Module selectedValue, boolean finalChoice)
			{
				PropertiesComponent.getInstance(selectedValue.getProject()).setValue(GROOVY_LAST_MODULE, selectedValue.getName());
				callback.accept(selectedValue);
				return null;
			}
		};

		final String lastModuleName = PropertiesComponent.getInstance(project).getValue(GROOVY_LAST_MODULE);
		if(lastModuleName != null)
		{
			int defaultOption = ContainerUtil.indexOf(modules, new Condition<Module>()
			{
				@Override
				public boolean value(Module module)
				{
					return module.getName().equals(lastModuleName);
				}
			});
			if(defaultOption >= 0)
			{
				step.setDefaultOptionIndex(defaultOption);
			}
		}
		final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
		if(context == null)
		{
			listPopup.showCenteredInCurrentWindow(project);
		}
		else
		{
			listPopup.showInBestPositionFor(context);
		}
	}

	@Nonnull
	private static Condition<Module> isGroovyCompatibleModule(final Condition<Module> condition)
	{
		return module -> {
			if(condition.value(module))
			{
				final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
				if(sdk != null && sdk.getSdkType() instanceof JavaSdkType)
				{
					return true;
				}
			}
			return false;
		};
	}

	public static List<Module> filterGroovyCompatibleModules(Collection<Module> modules, final Condition<Module> condition)
	{
		return ContainerUtil.filter(modules, isGroovyCompatibleModule(condition));
	}

	public static boolean hasGroovyCompatibleModules(Collection<Module> modules, final Condition<Module> condition)
	{
		return ContainerUtil.or(modules, isGroovyCompatibleModule(condition));
	}
}