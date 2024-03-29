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

import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import consulo.module.Module;
import consulo.language.psi.PsiDirectory;
import consulo.ui.image.Image;

/**
* @author peter
*/
public class TestsTopLevelDirectoryNode extends TopLevelDirectoryNode {
  private final Image myMethodIcon;

  public TestsTopLevelDirectoryNode(Module module,
                                    PsiDirectory testDir,
                                    ViewSettings viewSettings,
                                    final String title,
                                    final Image icon, final Image methodIcon) {
    super(module, testDir, viewSettings, title, icon, TESTS_FOLDER);
    myMethodIcon = methodIcon;
  }

  @Override
  protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
    return new TestClassNode(getModule(), typeDefinition, getSettings(), myMethodIcon);
  }
}
