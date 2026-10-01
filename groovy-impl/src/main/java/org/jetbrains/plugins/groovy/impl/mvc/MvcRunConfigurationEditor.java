/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.application.concurrent.coroutine.ReadLock;
import consulo.configurable.ConfigurationException;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.localize.ExecutionLocalize;
import consulo.execution.ui.awt.EnvironmentVariablesTextFieldWithBrowseButton;
import consulo.groovy.localize.GroovyLocalize;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.cmd.ParametersListUtil;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.TextBoxWithExpandAction;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.runner.RunConfigurationModuleBox;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MvcRunConfigurationEditor<T extends MvcRunConfiguration> extends SettingsEditor<T> {
    private static final int MAX_PRESENTABLE_CLASSPATH_LENGTH = 70;

    private final Project myProject;
    private final List<Component> myExtensions = new ArrayList<>();

    @Nullable
    private MvcFramework myFramework;
    @Nullable
    private Panel myPanel;

    public MvcRunConfigurationEditor(Project project) {
        myProject = project;
    }

    @RequiredUIAccess
    @Override
    protected void resetEditorFrom(T configuration) {
        Panel panel = myPanel;
        if (panel != null) {
            panel.reset(configuration);
        }
    }

    protected boolean isAvailableDepsClasspath() {
        return true;
    }

    @RequiredUIAccess
    protected void commandLineChanged(@Nonnull String newText) {
    }

    @RequiredUIAccess
    protected static void setCBEnabled(boolean enabled, CheckBox checkBox) {
        boolean wasEnabled = checkBox.isEnabled();
        checkBox.setEnabled(enabled);
        if (wasEnabled && !enabled) {
            checkBox.setValue(false);
        }
        else if (!wasEnabled && enabled) {
            checkBox.setValue(true);
        }
    }

    @RequiredUIAccess
    @Override
    protected void applyEditorTo(T configuration) throws ConfigurationException {
        Panel panel = myPanel;
        if (panel != null) {
            panel.apply(configuration);
        }
    }

    @RequiredUIAccess
    protected String getCommandLine() {
        Panel panel = myPanel;
        return panel == null ? "" : StringUtil.notNullize(panel.myCommandLine.getValue()).trim();
    }

    @Nullable
    @RequiredUIAccess
    protected Module getSelectedModule() {
        Panel panel = myPanel;
        return panel == null ? null : panel.myModulesBox.getValue();
    }

    @RequiredUIAccess
    public void addExtension(Component component) {
        myExtensions.add(component);
        Panel panel = myPanel;
        if (panel != null) {
            panel.myExtensionLayout.add(component);
        }
    }

    @RequiredUIAccess
    @Override
    protected Component createUIComponent() {
        Panel panel = new Panel();
        myPanel = panel;
        return panel.build();
    }

    @Override
    protected void disposeEditor() {
        Panel panel = myPanel;
        if (panel != null) {
            panel.cancelLoading();
        }
    }

    private static String getDepsClasspath(@Nullable MvcFramework framework, @Nullable Module module) {
        if (framework == null || module == null || module.isDisposed() || MvcFramework.getInstance(module) == null) {
            return "";
        }
        return framework.getApplicationClassPath(module).getPathsString();
    }

    private class Panel {
        private final RunConfigurationModuleBox myModulesBox;
        private final TextBox myCommandLine;
        private final TextBoxWithExpandAction myVMParameters;
        private final EnvironmentVariablesTextFieldWithBrowseButton myEnvVariablesComponent;
        private final CheckBox myDepsClasspath;
        private final VerticalLayout myExtensionLayout;

        private boolean myStoredDepsClasspath;
        private int myClasspathGeneration;

        @RequiredUIAccess
        private Panel() {
            myModulesBox = new RunConfigurationModuleBox();
            myModulesBox.addValueListener(event -> refreshDepsClasspath(false));

            myCommandLine = TextBox.create();
            myCommandLine.addValueListener(event -> commandLineChanged(getCommandLine()));

            myVMParameters = TextBoxWithExpandAction.create(
                PlatformIconGroup.actionsShow(),
                GroovyLocalize.runConfigurationVmOptionsDialogTitle().get(),
                ParametersListUtil.DEFAULT_LINE_PARSER,
                ParametersListUtil.DEFAULT_LINE_JOINER
            );

            myEnvVariablesComponent = new EnvironmentVariablesTextFieldWithBrowseButton();

            myDepsClasspath = CheckBox.create(GroovyLocalize.mvcRunConfigurationDepsClasspathCheckbox());

            myExtensionLayout = VerticalLayout.create();
            for (Component extension : myExtensions) {
                myExtensionLayout.add(extension);
            }
        }

        @RequiredUIAccess
        private Component build() {
            FormBuilder builder = FormBuilder.create();
            builder.addLabeled(GroovyLocalize.runConfigurationModuleChooserLabel(), myModulesBox.getComponent());
            builder.addLabeled(GroovyLocalize.mvcRunConfigurationCommandLineLabel(), myCommandLine);
            builder.addLabeled(ExecutionLocalize.runConfigurationJavaVmParametersLabel(), myVMParameters);
            builder.addLabeled(
                LocalizeValue.join(ExecutionLocalize.environmentVariablesComponentTitle(), LocalizeValue.colon()),
                myEnvVariablesComponent.getComponent()
            );
            builder.addBottom(myDepsClasspath);
            builder.addBottom(myExtensionLayout);
            return builder.build();
        }

        @RequiredUIAccess
        private void reset(T configuration) {
            myFramework = configuration.getFramework();
            myVMParameters.setValue(StringUtil.notNullize(configuration.vmParams));

            myCommandLine.setValue(StringUtil.notNullize(configuration.cmdLine));

            myModulesBox.reset(myProject, configuration.getModule(), () -> new ArrayList<>(configuration.getValidModules()));

            commandLineChanged(getCommandLine());

            myEnvVariablesComponent.setEnvs(new HashMap<>(configuration.envs));
            myEnvVariablesComponent.setPassParentEnvs(configuration.passParentEnv);

            myStoredDepsClasspath = configuration.depsClasspath;
            if (myDepsClasspath.isEnabled()) {
                myDepsClasspath.setValue(configuration.depsClasspath);
            }
            refreshDepsClasspath(true);
        }

        @RequiredUIAccess
        private void apply(T configuration) {
            configuration.setModule(myModulesBox.getValue());
            configuration.vmParams = StringUtil.notNullize(myVMParameters.getValue()).trim();
            configuration.cmdLine = getCommandLine();
            configuration.setEnvs(myEnvVariablesComponent.getEnvs());
            configuration.setPassParentEnvs(myEnvVariablesComponent.isPassParentEnvs());

            if (myDepsClasspath.isEnabled()) {
                configuration.depsClasspath = myDepsClasspath.getValueOrError();
            }
        }

        @RequiredUIAccess
        private void refreshDepsClasspath(boolean restoreStoredValue) {
            MvcFramework framework = myFramework;
            Module module = myModulesBox.getValue();
            int generation = ++myClasspathGeneration;
            CoroutineScope.launchAsync(
                myProject.coroutineContext(),
                () -> Coroutine
                    .first(ReadLock.<Void, String>apply(ignored -> getDepsClasspath(framework, module)))
                    .then(UIAction.<String, Void>apply(depsClasspath -> {
                        if (generation == myClasspathGeneration) {
                            updateDepsClasspath(depsClasspath, restoreStoredValue);
                        }
                        return null;
                    }))
            );
        }

        @RequiredUIAccess
        private void updateDepsClasspath(String depsClasspath, boolean restoreStoredValue) {
            boolean hasClasspath = StringUtil.isNotEmpty(depsClasspath);
            setCBEnabled(hasClasspath && isAvailableDepsClasspath(), myDepsClasspath);
            if (restoreStoredValue && myDepsClasspath.isEnabled()) {
                myDepsClasspath.setValue(myStoredDepsClasspath);
            }

            if (hasClasspath) {
                String presentable = StringUtil.first(depsClasspath, MAX_PRESENTABLE_CLASSPATH_LENGTH, true);
                myDepsClasspath.setLabelText(GroovyLocalize.mvcRunConfigurationDepsClasspathCheckboxWithPath(presentable));
                myDepsClasspath.setToolTipText(LocalizeValue.of(depsClasspath.replace(File.pathSeparator, "\n")));
            }
            else {
                myDepsClasspath.setLabelText(GroovyLocalize.mvcRunConfigurationDepsClasspathCheckbox());
                myDepsClasspath.setToolTipText(LocalizeValue.empty());
            }
        }

        private void cancelLoading() {
            myClasspathGeneration++;
            myModulesBox.cancelLoading();
        }
    }
}
