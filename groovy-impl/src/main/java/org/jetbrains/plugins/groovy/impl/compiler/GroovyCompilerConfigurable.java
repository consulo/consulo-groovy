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

package org.jetbrains.plugins.groovy.impl.compiler;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBUI;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyCompilerConfigurable implements ProjectConfigurable, Configurable.NoScroll {
    private JTextField myHeapSize;
    private JPanel myMainPanel;
    private JPanel myExcludesPanel;
    private JBCheckBox myInvokeDynamicSupportCB;

    private final ExcludedEntriesConfigurable myExcludes;
    private final GroovyCompilerConfiguration myConfig;

    @Inject
    public GroovyCompilerConfigurable(Project project, GroovyCompilerConfiguration compilerConfiguration) {
        myConfig = compilerConfiguration;
        myExcludes = createExcludedConfigurable(project);
        init();
    }

    public ExcludedEntriesConfigurable getExcludes() {
        return myExcludes;
    }

    private ExcludedEntriesConfigurable createExcludedConfigurable(Project project) {
        ExcludedEntriesConfiguration configuration = myConfig.getExcludeFromStubGeneration();
        final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true) {
            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                return super.isFileVisible(file, showHiddenFiles) && !index.isIgnored(file);
            }
        };
        Module[] modules = ModuleManager.getInstance(project).getModules();
        List<VirtualFile> roots =
            Arrays.stream(modules).flatMap(module -> Arrays.stream(ModuleRootManager.getInstance(module).getSourceRoots())).toList();
        descriptor.setRoots(roots);
        return new ExcludedEntriesConfigurable(project, descriptor, configuration);
    }


    @Override
    @Nonnull
    public String getId() {
        return "Groovy compiler";
    }

    @Nullable
    @Override
    public String getParentId() {
        return StandardConfigurableIds.COMPILER_GROUP;
    }

    @Override
    @Nonnull
    @Nls
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Groovy Compiler");
    }

    @Override
    public String getHelpTopic() {
        return "reference.projectsettings.compiler.groovy";
    }

    @Override
    public JComponent createComponent(@Nonnull Disposable uiDisposable) {
        myExcludesPanel.add(myExcludes.createComponent());
        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        return !Comparing.equal(myConfig.getHeapSize(), myHeapSize.getText()) ||
            myInvokeDynamicSupportCB.isSelected() != myConfig.isInvokeDynamic() ||
            myExcludes.isModified();
    }

    @Override
    public void apply() throws ConfigurationException {
        myExcludes.apply();
        myConfig.setHeapSize(myHeapSize.getText());
        myConfig.setInvokeDynamic(myInvokeDynamicSupportCB.isSelected());
    }

    @Override
    public void reset() {
        myHeapSize.setText(myConfig.getHeapSize());
        myInvokeDynamicSupportCB.setSelected(myConfig.isInvokeDynamic());
        myExcludes.reset();
    }

    @Override
    public void disposeUIResources() {
        myExcludes.disposeUIResources();
    }

    private void init() {
        myMainPanel = new JPanel();
        myMainPanel.setLayout(new GridLayoutManager(3, 1, JBUI.emptyInsets(), -1, -1));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, JBUI.emptyInsets(), -1, -1));
        myMainPanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Maximum heap size (MB):");
        panel1.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myHeapSize = new JTextField();
        panel1.add(myHeapSize, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel1.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        myExcludesPanel = new JPanel();
        myExcludesPanel.setLayout(new BorderLayout(0, 0));
        myMainPanel.add(myExcludesPanel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myExcludesPanel.setBorder(IdeBorderFactory.createTitledBorder("Exclude from stub generation", false));
        myInvokeDynamicSupportCB = new JBCheckBox();
        myInvokeDynamicSupportCB.setText("Invoke dynamic support");
        myInvokeDynamicSupportCB.setMnemonic('D');
        myInvokeDynamicSupportCB.setDisplayedMnemonicIndex(7);
        myMainPanel.add(myInvokeDynamicSupportCB, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }
}
