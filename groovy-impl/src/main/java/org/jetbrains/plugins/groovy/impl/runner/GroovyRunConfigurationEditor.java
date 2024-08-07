/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.runner;

import consulo.application.AllIcons;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.ui.awt.EnvironmentVariablesComponent;
import consulo.execution.ui.awt.RawCommandLineEditor;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.module.Module;
import consulo.module.ModulesAlphaComparator;
import consulo.ui.ex.awt.*;
import consulo.ui.ex.awt.util.BrowseFilesListener;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroovyRunConfigurationEditor extends SettingsEditor<GroovyScriptRunConfiguration> implements PanelWithAnchor {
  private DefaultComboBoxModel myModulesModel;
  private JComboBox myModulesBox;
  private JPanel myMainPanel;
  private RawCommandLineEditor myVMParameters;
  private RawCommandLineEditor myParameters;
  private JPanel scriptPathPanel;
  private JPanel workDirPanel;
  private JCheckBox myDebugCB;
  private EnvironmentVariablesComponent myEnvVariables;
  private JBLabel myScriptParametersLabel;
  private final JTextField scriptPathField;
  private final JTextField workDirField;
  private JComponent anchor;

  public GroovyRunConfigurationEditor() {

    scriptPathField = new JTextField();
    final BrowseFilesListener scriptBrowseListener = new BrowseFilesListener(scriptPathField,
                                                                             "Script Path",
                                                                             "Specify path to script",
                                                                             new FileChooserDescriptor(true,
                                                                                                       false,
                                                                                                       false,
                                                                                                       false,
                                                                                                       false,
                                                                                                       false) {
                                                                               public boolean isFileSelectable(VirtualFile file) {
                                                                                 return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
                                                                               }
                                                                             });
    final FieldPanel scriptFieldPanel = new FieldPanel(scriptPathField, null, null, scriptBrowseListener, null);
    scriptPathPanel.setLayout(new BorderLayout());
    scriptPathPanel.add(scriptFieldPanel, BorderLayout.CENTER);

    workDirField = new JTextField();
    final BrowseFilesListener workDirBrowseFilesListener = new BrowseFilesListener(workDirField,
                                                                                   "Working directory",
                                                                                   "Specify working directory",
                                                                                   BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    final FieldPanel workDirFieldPanel = new FieldPanel(workDirField, null, null, workDirBrowseFilesListener, null);
    workDirPanel.setLayout(new BorderLayout());
    workDirPanel.add(workDirFieldPanel, BorderLayout.CENTER);

    setAnchor(myEnvVariables.getLabel());
  }

  public void resetEditorFrom(GroovyScriptRunConfiguration configuration) {
    myVMParameters.setDialogCaption("VM Options");
    myVMParameters.setText(configuration.getVMParameters());

    myParameters.setDialogCaption("Script Parameters");
    myParameters.setText(configuration.getScriptParameters());

    scriptPathField.setText(configuration.getScriptPath());
    workDirField.setText(configuration.getWorkDir());

    myDebugCB.setEnabled(true);
    myDebugCB.setSelected(configuration.isDebugEnabled());

    myModulesModel.removeAllElements();
    List<Module> modules = new ArrayList<Module>(configuration.getValidModules());
    Collections.sort(modules, ModulesAlphaComparator.INSTANCE);
    for (Module module : modules) {
      myModulesModel.addElement(module);
    }
    myModulesModel.setSelectedItem(configuration.getModule());

    myEnvVariables.setEnvs(configuration.getEnvs());
  }

  public void applyEditorTo(GroovyScriptRunConfiguration configuration) throws ConfigurationException {
    configuration.setModule((Module)myModulesBox.getSelectedItem());
    configuration.setVMParameters(myVMParameters.getText());
    configuration.setDebugEnabled(myDebugCB.isSelected());
    configuration.setScriptParameters(myParameters.getText());
    configuration.setScriptPath(scriptPathField.getText().trim());
    configuration.setWorkDir(workDirField.getText().trim());
    configuration.setEnvs(myEnvVariables.getEnvs());
  }

  @Nonnull
  public JComponent createEditor() {
    myModulesModel = new DefaultComboBoxModel();
    myModulesBox.setModel(myModulesModel);
    myDebugCB.setEnabled(true);
    myDebugCB.setSelected(false);

    myModulesBox.setRenderer(new ColoredListCellRenderer<Module>() {
      @Override
      protected void customizeCellRenderer(@Nonnull JList<? extends Module> jList, Module module, int i, boolean b, boolean b1) {
        if (module != null) {
          setIcon(AllIcons.Nodes.Module);
          append(module.getName());
        }

      }
    });
    new ComboboxSpeedSearch(myModulesBox) {
      @Override
      protected String getElementText(Object element) {
        return element instanceof Module ? ((Module)element).getName() : "";
      }
    };

    return myMainPanel;
  }

  public void disposeEditor() {
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myScriptParametersLabel.setAnchor(anchor);
    myEnvVariables.setAnchor(anchor);
  }
}
