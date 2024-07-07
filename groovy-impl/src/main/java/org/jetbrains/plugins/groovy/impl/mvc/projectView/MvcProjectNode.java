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

package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.tree.ModuleGroup;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ide.impl.idea.ide.projectView.impl.nodes.AbstractProjectNode;
import consulo.project.ui.view.tree.AbstractTreeNode;
import org.jetbrains.plugins.groovy.impl.mvc.MvcModuleStructureUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class MvcProjectNode extends AbstractProjectNode {
  private final MvcToolWindowDescriptor myDescriptor;

  public MvcProjectNode(final Project project, final ViewSettings viewSettings, MvcToolWindowDescriptor descriptor) {
    super(project, project, viewSettings);
    myDescriptor = descriptor;
  }

  @Nonnull
  public Collection<? extends AbstractTreeNode> getChildren() {
    List<Module> modules = MvcModuleStructureUtil.getAllModulesWithSupport(myProject, myDescriptor.getFramework());

    modules = myDescriptor.getFramework().reorderModulesForMvcView(modules);
    
    final ArrayList<AbstractTreeNode> nodes = new ArrayList<AbstractTreeNode>();
    for (Module module : modules) {
      nodes.add(new MvcModuleNode(module, getSettings(), myDescriptor));
    }
    return nodes;
  }

  @Override
  public boolean isAlwaysExpand() {
    return true;
  }

  protected AbstractTreeNode createModuleGroup(final Module module) {
    return new MvcProjectNode(getProject(), getSettings(), myDescriptor);
  }

  protected AbstractTreeNode createModuleGroupNode(final ModuleGroup moduleGroup) {
    return new MvcProjectNode(getProject(), getSettings(), myDescriptor);
  }

}