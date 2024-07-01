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
package org.jetbrains.plugins.groovy.impl.doc.actions;

import com.intellij.java.language.impl.ui.PackageChooser;
import com.intellij.java.language.impl.ui.PackageChooserFactory;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.application.dumb.DumbAware;
import consulo.ide.impl.idea.util.IconUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import org.jetbrains.plugins.groovy.impl.doc.GroovyDocConfiguration;

import javax.swing.*;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 14.10.2008
 */
public class GroovyDocAddPackageAction extends AnAction implements DumbAware {
  private final DefaultListModel myDataModel;

  public GroovyDocAddPackageAction(final DefaultListModel dataModel) {
    super("Add package", "Add package", IconUtil.getAddIcon());
    myDataModel = dataModel;
  }

  @RequiredUIAccess
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(Project.KEY);
    if (project == null) {
      return;
    }

    PackageChooser chooser = project.getInstance(PackageChooserFactory.class).create();

    final List<PsiJavaPackage> packages = chooser.showAndSelect();
    if (packages == null) {
      return;
    }

    for (PsiJavaPackage aPackage : packages) {
      final String qualifiedName = aPackage.getQualifiedName();

      if (qualifiedName.isEmpty()) {
        myDataModel.addElement(GroovyDocConfiguration.ALL_PACKAGES);
      }
      myDataModel.addElement(qualifiedName);
    }
  }
}
