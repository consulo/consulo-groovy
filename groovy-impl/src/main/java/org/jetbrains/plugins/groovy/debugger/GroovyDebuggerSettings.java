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
package org.jetbrains.plugins.groovy.debugger;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import consulo.options.SimpleConfigurableByProperties;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.RequiredUIAccess;
import consulo.ui.VerticalLayout;
import consulo.ui.style.StandardColors;

/**
 * @author ilyas
 */
public class GroovyDebuggerSettings extends XDebuggerSettings<GroovyDebuggerSettings>
{
	private static class GroovySteppingConfigurable extends SimpleConfigurableByProperties implements Configurable
	{
		@Override
		public String getHelpTopic()
		{
			return "reference.idesettings.debugger.groovy";
		}

		@Nls
		@Override
		public String getDisplayName()
		{
			return GroovyBundle.message("groovy.debug.caption");
		}

		@RequiredUIAccess
		@Nonnull
		@Override
		protected Component createLayout(PropertyBuilder propertyBuilder)
		{
			GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();

			VerticalLayout verticalLayout = VerticalLayout.create();
			CheckBox disableSpecificCheckBox = CheckBox.create(GroovyBundle.message("groovy.debug.disable.specific.methods"));
			verticalLayout.add(disableSpecificCheckBox);
			propertyBuilder.add(disableSpecificCheckBox, settings::getDebugDisableSpecificMethods, settings::setDebugDisableSpecificMethods);
			return verticalLayout;
		}
	}

	private static final class GroovyHotSwapConfigurable extends SimpleConfigurableByProperties implements Configurable
	{
		@Override
		public String getHelpTopic()
		{
			return "reference.idesettings.debugger.groovy";
		}

		@Nls
		@Override
		public String getDisplayName()
		{
			return GroovyBundle.message("groovy.debug.caption");
		}

		@RequiredUIAccess
		@Nonnull
		@Override
		protected Component createLayout(PropertyBuilder propertyBuilder)
		{
			GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();

			VerticalLayout verticalLayout = VerticalLayout.create();
			CheckBox hotSwapCheckBox = CheckBox.create("Enable hot-swap agent for Groovy code");
			verticalLayout.add(hotSwapCheckBox);
			propertyBuilder.add(hotSwapCheckBox, settings::isEnableHotSwap, settings::setEnableHotSwap);
			verticalLayout.add(Label.create("May cause serialization issues in the debugged application").setForeground(StandardColors.GRAY));
			return verticalLayout;
		}
	}

	public boolean DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = true;
	public boolean ENABLE_GROOVY_HOTSWAP = true;

	public GroovyDebuggerSettings()
	{
		super("groovy_debugger");
	}

	@Nonnull
	@Override
	public Collection<? extends Configurable> createConfigurables(@Nonnull DebuggerSettingsCategory category)
	{
		switch(category)
		{
			case STEPPING:
				return Collections.singletonList(new GroovySteppingConfigurable());
			case HOTSWAP:
				return Collections.singletonList(new GroovyHotSwapConfigurable());
		}
		return Collections.emptyList();
	}

	@Override
	public GroovyDebuggerSettings getState()
	{
		return this;
	}

	@Override
	public void loadState(final GroovyDebuggerSettings state)
	{
		XmlSerializerUtil.copyBean(state, this);
	}

	public void setDebugDisableSpecificMethods(boolean debugDisableSpecificMethods)
	{
		DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = debugDisableSpecificMethods;
	}

	public boolean getDebugDisableSpecificMethods()
	{
		return DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
	}

	public boolean isEnableHotSwap()
	{
		return ENABLE_GROOVY_HOTSWAP;
	}

	public void setEnableHotSwap(boolean enableHotSwap)
	{
		ENABLE_GROOVY_HOTSWAP = enableHotSwap;
	}

	@Nonnull
	public static GroovyDebuggerSettings getInstance()
	{
		return getInstance(GroovyDebuggerSettings.class);
	}
}