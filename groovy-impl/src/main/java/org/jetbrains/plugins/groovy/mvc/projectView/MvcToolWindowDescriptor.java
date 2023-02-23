/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.mvc.projectView;

import consulo.application.dumb.DumbAware;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author peter
 */
public abstract class MvcToolWindowDescriptor implements ToolWindowFactory, Condition<Project>, DumbAware {
  private final MvcFramework myFramework;

  public MvcToolWindowDescriptor(MvcFramework framework) {
    myFramework = framework;
  }

  public MvcFramework getFramework() {
    return myFramework;
  }

  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    toolWindow.setAvailable(true, null);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(myFramework.getDisplayName());

    MvcProjectViewPane view = new MvcProjectViewPane(project, this);
    view.setup((consulo.ide.impl.idea.openapi.wm.ex.ToolWindowEx)toolWindow);
  }

  public boolean value(Project project) {
    return MvcModuleStructureUtil.hasModulesWithSupport(project, myFramework);
  }

  public String getToolWindowId() {
    return getToolWindowId(myFramework);
  }

  public static String getToolWindowId(final MvcFramework framework) {
    return framework.getFrameworkName() + " View";
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO(myFramework.getDisplayName());
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return myFramework.getToolWindowIcon();
  }

  @Nonnull
  @Override
  public String getId() {
    return getToolWindowId();
  }

  @Nonnull
  @Override
  public ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  public abstract void fillModuleChildren(List<AbstractTreeNode> result,
                                          final Module module,
                                          final ViewSettings viewSettings,
                                          VirtualFile root);

  public abstract Image getModuleNodeIcon();

  @Nonnull
  public abstract MvcProjectViewState getProjectViewState(Project project);

  @Nullable
  protected static PsiDirectory findDirectory(Project project, VirtualFile root, @Nonnull String relativePath) {
    final VirtualFile file = root.findFileByRelativePath(relativePath);
    return file == null ? null : PsiManager.getInstance(project).findDirectory(file);
  }

}
