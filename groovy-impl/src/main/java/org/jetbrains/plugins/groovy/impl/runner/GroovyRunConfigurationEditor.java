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

import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.EnvironmentVariablesTextFieldWithBrowseButton;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.groovy.localize.GroovyLocalize;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModulesAlphaComparator;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

import java.util.ArrayList;
import java.util.List;

public class GroovyRunConfigurationEditor extends SettingsEditor<GroovyScriptRunConfiguration> {
    private final Project myProject;

    @Nullable
    private Panel myPanel;

    public GroovyRunConfigurationEditor(Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        Panel panel = new Panel();
        myPanel = panel;
        return panel.build();
    }

    @RequiredUIAccess
    @Override
    public void resetEditorFrom(GroovyScriptRunConfiguration configuration) {
        Panel panel = myPanel;
        if (panel != null) {
            panel.reset(configuration);
        }
    }

    @RequiredUIAccess
    @Override
    public void applyEditorTo(GroovyScriptRunConfiguration configuration) throws ConfigurationException {
        Panel panel = myPanel;
        if (panel != null) {
            panel.apply(configuration);
        }
    }

    @Override
    public void disposeEditor() {
        Panel panel = myPanel;
        if (panel != null) {
            panel.myModulesBox.cancelLoading();
        }
    }

    @RequiredUIAccess
    private static TextBoxWithExpandAction createParametersField(LocalizeValue dialogTitle) {
        return TextBoxWithExpandAction.create(
            PlatformIconGroup.actionsShow(),
            dialogTitle.get(),
            ParametersListUtil.DEFAULT_LINE_PARSER,
            ParametersListUtil.DEFAULT_LINE_JOINER
        );
    }

    private class Panel {
        private final FileChooserTextBoxBuilder.Controller myScriptPathField;
        private final RunConfigurationModuleBox myModulesBox;
        private final TextBoxWithExpandAction myVMParameters;
        private final TextBoxWithExpandAction myParameters;
        private final EnvironmentVariablesTextFieldWithBrowseButton myEnvVariables;
        private final FileChooserTextBoxBuilder.Controller myWorkDirField;
        private final CheckBox myDebugCB;

        @RequiredUIAccess
        private Panel() {
            myScriptPathField = FileChooserTextBoxBuilder.create(myProject)
                .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileDescriptor(GroovyFileType.INSTANCE))
                .dialogTitle(GroovyLocalize.scriptRunnerChooserTitle())
                .dialogDescription(GroovyLocalize.scriptRunnerChooserDescription())
                .build();

            myModulesBox = new RunConfigurationModuleBox();

            myVMParameters = createParametersField(GroovyLocalize.runConfigurationVmOptionsDialogTitle());
            myParameters = createParametersField(GroovyLocalize.runConfigurationScriptParametersDialogTitle());

            myEnvVariables = new EnvironmentVariablesTextFieldWithBrowseButton();

            myWorkDirField = FileChooserTextBoxBuilder.create(myProject)
                .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor())
                .dialogTitle(ExecutionLocalize.selectWorkingDirectoryMessage())
                .build();

            myDebugCB = CheckBox.create(GroovyLocalize.debugOption());
        }

        @RequiredUIAccess
        private Component build() {
            FormBuilder builder = FormBuilder.create();
            builder.addLabeled(GroovyLocalize.runConfigurationScriptPathLabel(), myScriptPathField.getComponent());
            builder.addLabeled(GroovyLocalize.runConfigurationModuleChooserLabel(), myModulesBox.getComponent());
            builder.addLabeled(ExecutionLocalize.runConfigurationJavaVmParametersLabel(), myVMParameters);
            builder.addLabeled(GroovyLocalize.runConfigurationScriptParametersLabel(), myParameters);
            builder.addLabeled(
                LocalizeValue.join(ExecutionLocalize.environmentVariablesComponentTitle(), LocalizeValue.colon()),
                myEnvVariables.getComponent()
            );
            builder.addLabeled(ExecutionLocalize.runConfigurationWorkingDirectoryLabel(), myWorkDirField.getComponent());
            builder.addBottom(myDebugCB);
            return builder.build();
        }

        @RequiredUIAccess
        private void reset(GroovyScriptRunConfiguration configuration) {
            myScriptPathField.setValue(StringUtil.notNullize(configuration.getScriptPath()));
            myModulesBox.reset(myProject, configuration.getModule(), () -> {
                List<Module> modules = new ArrayList<>(configuration.getValidModules());
                modules.sort(ModulesAlphaComparator.INSTANCE);
                return modules;
            });
            myVMParameters.setValue(StringUtil.notNullize(configuration.getVMParameters()));
            myParameters.setValue(StringUtil.notNullize(configuration.getScriptParameters()));
            myEnvVariables.setEnvs(configuration.getEnvs());
            myEnvVariables.setPassParentEnvs(configuration.isPassParentEnvs());
            myWorkDirField.setValue(StringUtil.notNullize(configuration.getWorkDir()));
            myDebugCB.setValue(configuration.isDebugEnabled());
        }

        @RequiredUIAccess
        private void apply(GroovyScriptRunConfiguration configuration) {
            configuration.setModule(myModulesBox.getValue());
            configuration.setVMParameters(StringUtil.notNullize(myVMParameters.getValue()));
            configuration.setDebugEnabled(myDebugCB.getValueOrError());
            configuration.setScriptParameters(StringUtil.notNullize(myParameters.getValue()));
            configuration.setScriptPath(myScriptPathField.getValue().trim());
            configuration.setWorkDir(myWorkDirField.getValue().trim());
            configuration.setEnvs(myEnvVariables.getEnvs());
            configuration.setPassParentEnvs(myEnvVariables.isPassParentEnvs());
        }
    }
}
