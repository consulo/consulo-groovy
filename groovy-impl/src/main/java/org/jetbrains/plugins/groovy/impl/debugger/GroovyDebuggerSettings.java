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
package org.jetbrains.plugins.groovy.impl.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.execution.debug.setting.DebuggerSettingsCategory;
import consulo.execution.debug.setting.XDebuggerSettings;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.StandardColors;
import consulo.util.xml.serializer.XmlSerializerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyDebuggerSettings extends XDebuggerSettings<GroovyDebuggerSettings> {
  private static class GroovySteppingConfigurable extends SimpleConfigurableByProperties implements Configurable {
    @Override
    public String getHelpTopic() {
      return "reference.idesettings.debugger.groovy";
    }

    @Nls
    @Override
    public String getDisplayName() {
      return GroovyBundle.message("groovy.debug.caption");
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
      GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();

      VerticalLayout verticalLayout = VerticalLayout.create();
      CheckBox disableSpecificCheckBox =
        CheckBox.create(LocalizeValue.localizeTODO(GroovyBundle.message("groovy.debug.disable.specific.methods")));
      verticalLayout.add(disableSpecificCheckBox);
      propertyBuilder.add(disableSpecificCheckBox, settings::getDebugDisableSpecificMethods, settings::setDebugDisableSpecificMethods);
      return verticalLayout;
    }
  }

  private static final class GroovyHotSwapConfigurable extends SimpleConfigurableByProperties implements Configurable {
    @Override
    public String getHelpTopic() {
      return "reference.idesettings.debugger.groovy";
    }

    @Nls
    @Override
    public String getDisplayName() {
      return GroovyBundle.message("groovy.debug.caption");
    }

    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
      GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();

      VerticalLayout verticalLayout = VerticalLayout.create();
      CheckBox hotSwapCheckBox = CheckBox.create(LocalizeValue.localizeTODO("Enable hot-swap agent for Groovy code"));
      verticalLayout.add(hotSwapCheckBox);
      propertyBuilder.add(hotSwapCheckBox, settings::isEnableHotSwap, settings::setEnableHotSwap);
      verticalLayout.add(Label.create(LocalizeValue.localizeTODO("May cause serialization issues in the debugged application"))
                              .withForegroundColor(StandardColors.GRAY));
      return verticalLayout;
    }
  }

  public boolean DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = true;
  public boolean ENABLE_GROOVY_HOTSWAP = true;

  public GroovyDebuggerSettings() {
    super("groovy_debugger");
  }

  @Nonnull
  @Override
  public Collection<? extends Configurable> createConfigurables(@Nonnull DebuggerSettingsCategory category) {
    switch (category) {
      case STEPPING:
        return Collections.singletonList(new GroovySteppingConfigurable());
      case HOTSWAP:
        return Collections.singletonList(new GroovyHotSwapConfigurable());
    }
    return Collections.emptyList();
  }

  @Override
  public GroovyDebuggerSettings getState() {
    return this;
  }

  @Override
  public void loadState(final GroovyDebuggerSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setDebugDisableSpecificMethods(boolean debugDisableSpecificMethods) {
    DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS = debugDisableSpecificMethods;
  }

  public boolean getDebugDisableSpecificMethods() {
    return DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
  }

  public boolean isEnableHotSwap() {
    return ENABLE_GROOVY_HOTSWAP;
  }

  public void setEnableHotSwap(boolean enableHotSwap) {
    ENABLE_GROOVY_HOTSWAP = enableHotSwap;
  }

  @Nonnull
  public static GroovyDebuggerSettings getInstance() {
    return getInstance(GroovyDebuggerSettings.class);
  }
}