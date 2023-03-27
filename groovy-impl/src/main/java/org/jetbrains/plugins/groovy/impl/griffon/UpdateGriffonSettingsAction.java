/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.griffon;

import javax.annotation.Nonnull;

import consulo.application.AllIcons;
import consulo.ui.ex.action.AnActionEvent;
import consulo.module.Module;
import org.jetbrains.plugins.groovy.impl.mvc.MvcActionBase;
import org.jetbrains.plugins.groovy.impl.mvc.MvcFramework;

/**
 * @author peter
 */
public class UpdateGriffonSettingsAction extends MvcActionBase {

  @Override
  protected boolean isFrameworkSupported(@Nonnull MvcFramework framework) {
    return framework == GriffonFramework.getInstance();
  }

  @Override
  protected void actionPerformed(@Nonnull AnActionEvent e, @Nonnull final Module module, @Nonnull MvcFramework framework) {
    GriffonFramework.getInstance().updateProjectStructure(module);
  }

  @Override
  protected void updateView(AnActionEvent event, @Nonnull MvcFramework framework, @Nonnull Module module) {
    event.getPresentation().setIcon(AllIcons.Actions.Refresh);
  }

}