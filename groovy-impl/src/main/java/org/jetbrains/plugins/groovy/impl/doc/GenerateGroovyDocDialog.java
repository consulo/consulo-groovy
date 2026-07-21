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
package org.jetbrains.plugins.groovy.impl.doc;

import consulo.groovy.impl.localize.GroovyDocLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.io.File;

public final class GenerateGroovyDocDialog extends DialogWrapper {
  private final Project myProject;
  private final GroovyDocConfiguration myConfiguration;

  private GroovyDocGenerationPanel myPanel;

  public GenerateGroovyDocDialog(Project project, GroovyDocConfiguration configuration) {
    super(project, true);
    myProject = project;
    myConfiguration = configuration;

    setOKButtonText(GroovyDocLocalize.groovydocGenerateStartButton());
    setTitle(GroovyDocLocalize.groovydocGenerateTitle());

    init();
  }

  @Override
  @RequiredUIAccess
  protected JComponent createCenterPanel() {
    myPanel = new GroovyDocGenerationPanel();
    myPanel.reset(myConfiguration);
    return myPanel.getPanel();
  }

  @Override
  @RequiredUIAccess
  protected void doOKAction() {
    myPanel.apply(myConfiguration);
    if (checkDir(myConfiguration.OUTPUT_DIRECTORY, "output") && checkDir(myConfiguration.INPUT_DIRECTORY, "input")) {
      close(OK_EXIT_CODE);
    }
  }

  @Override
  protected void dispose() {
    super.dispose();
    //Disposer.dispose(myPanel);
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "editing.groovydocGeneration";
  }

  @RequiredUIAccess
  private boolean checkDir(String dirName, String dirPrefix) {
    if (dirName == null || dirName.trim().isEmpty()) {
      Messages.showMessageDialog(
        myProject,
        GroovyDocLocalize.groovydocGenerate0DirectoryNotSpecified(dirPrefix).get(),
        CommonLocalize.titleError().get(),
        UIUtil.getErrorIcon()
      );
      return false;
    }

    File dir = new File(dirName);
    if (dir.exists()) {
      if (!dir.isDirectory()) {
        showError(GroovyDocLocalize.groovydocGenerateNotADirectory(dirName));
        return false;
      }
    }
    else {
      int choice = Messages.showOkCancelDialog(
        myProject,
        GroovyDocLocalize.groovydocGenerateDirectoryNotExists(dirName).get(),
        GroovyDocLocalize.groovydocGenerateMessageTitle().get(),
        UIUtil.getWarningIcon()
      );
      if (choice != 0) return false;
      if (!dir.mkdirs()) {
        showError(GroovyDocLocalize.groovydocGenerateDirectoryCreationFailed(dirName));
        return false;
      }
    }
    return true;
  }

  @RequiredUIAccess
  private void showError(LocalizeValue message) {
    Messages.showMessageDialog(myProject, message.get(), CommonLocalize.titleError().get(), UIUtil.getErrorIcon());
  }
}
