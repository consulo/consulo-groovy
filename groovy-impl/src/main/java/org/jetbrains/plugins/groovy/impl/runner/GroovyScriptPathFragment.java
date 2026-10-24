/*
 * Copyright 2013-2026 consulo.io
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

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.execution.configuration.fragment.StandardRunConfigurationFragmentOrder;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.groovy.localize.GroovyLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * The script a {@link GroovyScriptRunConfiguration} runs.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class GroovyScriptPathFragment implements RunConfigurationFragment<GroovyScriptRunConfiguration> {
    @Override
    public String getId() {
        return "groovy.script.path";
    }

    @Override
    public LocalizeValue getName() {
        return GroovyLocalize.runConfigurationFragmentScriptPath();
    }

    @Override
    public int getOrder() {
        return StandardRunConfigurationFragmentOrder.FIRST;
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<GroovyScriptRunConfiguration> createEditor(Project project, Disposable parent) {
        FileChooserTextBoxBuilder.Controller field = FileChooserTextBoxBuilder.create(project)
            .uiDisposable(parent)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileDescriptor(GroovyFileType.INSTANCE))
            .dialogTitle(GroovyLocalize.scriptRunnerChooserTitle())
            .dialogDescription(GroovyLocalize.scriptRunnerChooserDescription())
            .build();

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(GroovyLocalize.runConfigurationScriptPathLabel(), field.getComponent());
            }

            @RequiredUIAccess
            @Override
            public void reset(GroovyScriptRunConfiguration target) {
                field.setValue(StringUtil.notNullize(target.getScriptPath()), false);
            }

            @RequiredUIAccess
            @Override
            public void apply(GroovyScriptRunConfiguration target) {
                target.setScriptPath(StringUtil.notNullize(field.getValue()).trim());
            }
        };
    }
}
