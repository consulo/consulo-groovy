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

import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.configurable.*;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.ide.impl.compiler.setting.ExcludedEntriesConfigurable;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import javax.swing.*;
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
  }

  public ExcludedEntriesConfigurable getExcludes() {
    return myExcludes;
  }

  private ExcludedEntriesConfigurable createExcludedConfigurable(final Project project) {
    final ExcludedEntriesConfiguration configuration = myConfig.getExcludeFromStubGeneration();
    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true) {
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


  @Nonnull
  public String getId() {
    return "Groovy compiler";
  }

  @Nullable
  @Override
  public String getParentId() {
    return StandardConfigurableIds.COMPILER_GROUP;
  }

  @Nonnull
  @Nls
  public String getDisplayName() {
    return "Groovy Compiler";
  }

  public String getHelpTopic() {
    return "reference.projectsettings.compiler.groovy";
  }

  public JComponent createComponent(@Nonnull Disposable uiDisposable) {
    myExcludesPanel.add(myExcludes.createComponent());
    return myMainPanel;
  }

  public boolean isModified() {
    return !Comparing.equal(myConfig.getHeapSize(), myHeapSize.getText()) ||
      myInvokeDynamicSupportCB.isSelected() != myConfig.isInvokeDynamic() ||
      myExcludes.isModified();
  }

  public void apply() throws ConfigurationException {
    myExcludes.apply();
    myConfig.setHeapSize(myHeapSize.getText());
    myConfig.setInvokeDynamic(myInvokeDynamicSupportCB.isSelected());
  }

  public void reset() {
    myHeapSize.setText(myConfig.getHeapSize());
    myInvokeDynamicSupportCB.setSelected(myConfig.isInvokeDynamic());
    myExcludes.reset();
  }

  public void disposeUIResources() {
    myExcludes.disposeUIResources();
  }
}
