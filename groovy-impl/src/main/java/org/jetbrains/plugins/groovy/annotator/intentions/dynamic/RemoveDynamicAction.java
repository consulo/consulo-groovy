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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;

import javax.swing.tree.TreePath;

/**
 * @author Max Medvedev
 */
public class RemoveDynamicAction extends AnAction {
  static final String GROOVY_DYNAMIC_REMOVE = "Groovy.Dynamic.Remove";

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DynamicToolWindowWrapper toolWindow = DynamicToolWindowWrapper.getInstance(e.getData(Project.KEY));

    toolWindow.deleteRow();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(Project.KEY);
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final TreePath[] paths = DynamicToolWindowWrapper.getInstance(project).getTreeTable().getTree().getSelectionPaths();
    e.getPresentation().setEnabled(paths != null);
  }
}
