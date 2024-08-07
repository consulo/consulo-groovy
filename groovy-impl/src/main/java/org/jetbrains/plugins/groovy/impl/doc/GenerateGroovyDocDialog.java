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

import consulo.application.CommonBundle;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;

import jakarta.annotation.Nullable;
import javax.swing.*;
import java.io.File;

public final class GenerateGroovyDocDialog extends DialogWrapper
{
  private final Project myProject;
  private final GroovyDocConfiguration myConfiguration;

  private GroovyDocGenerationPanel myPanel;

  public GenerateGroovyDocDialog(Project project, GroovyDocConfiguration configuration) {
    super(project, true);
    myProject = project;
    myConfiguration = configuration;

    setOKButtonText(GroovyDocBundle.message("groovydoc.generate.start.button"));
    setTitle(GroovyDocBundle.message("groovydoc.generate.title"));

    init();
  }

  protected JComponent createCenterPanel() {
    myPanel = new GroovyDocGenerationPanel();
    myPanel.reset(myConfiguration);
    return myPanel.getPanel();
  }

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

  private boolean checkDir(String dirName, String dirPrefix) {
    if (dirName == null || dirName.trim().length() == 0) {
      Messages.showMessageDialog(myProject, GroovyDocBundle.message("groovydoc.generate.0.directory.not.specified", dirPrefix),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return false;
    }

    File dir = new File(dirName);
    if (dir.exists()) {
      if (!dir.isDirectory()) {
        showError(GroovyDocBundle.message("groovydoc.generate.not.a.directory", dirName));
        return false;
      }
    }
    else {
      int choice = Messages.showOkCancelDialog(myProject,
                                               GroovyDocBundle.message("groovydoc.generate.directory.not.exists", dirName),
                                               GroovyDocBundle.message("groovydoc.generate.message.title"), Messages.getWarningIcon());
      if (choice != 0) return false;
      if (!dir.mkdirs()) {
        showError(GroovyDocBundle.message("groovydoc.generate.directory.creation.failed", dirName));
        return false;
      }
    }
    return true;
  }

  private void showError(final String message) {
    Messages.showMessageDialog(myProject, message, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
  }
}
