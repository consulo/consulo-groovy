/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.java.language.impl.JavaClassFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.compiler.CompileContext;
import consulo.compiler.CompilerManager;
import consulo.compiler.resourceCompiler.ResourceCompilerConfiguration;
import consulo.compiler.scope.CompileScope;
import consulo.content.bundle.Sdk;
import consulo.groovy.localize.GroovyLocalize;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FilenameIndex;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author Dmitry.Krasilschikov
 */
@ExtensionImpl(id = "groovy-compile", order = "after groovy-stub-generator")
public class GroovyCompiler extends GroovyCompilerBase {
  private static final String AST_TRANSFORM_FILE_NAME = "org.codehaus.groovy.transform.ASTTransformation";

  @Inject
  public GroovyCompiler(Project project) {
    super(project);
  }

  @Nonnull
  @Override
  public String getDescription() {
    return "groovy compiler";
  }

  @Override
  protected void compileFiles(CompileContext context,
                              Module module,
                              List<VirtualFile> toCompile,
                              OutputSink sink,
                              boolean tests) {
    context.getProgressIndicator().checkCanceled();
    context.getProgressIndicator().setText("Starting Groovy compiler...");

    runGroovycCompiler(context, module, toCompile, false, getMainOutput(context, module, tests), sink, tests);
  }

  @Override
  @RequiredUIAccess
  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(GroovyFileType.INSTANCE);
    if (files.length == 0) {
      return true;
    }

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    Set<Module> modules = new HashSet<>();
    for (VirtualFile file : files) {
      if (!StringUtil.equal(file.getExtension(),
                            GroovyFileType.DEFAULT_EXTENSION,
                            false) || compilerManager.isExcludedFromCompilation(file) ||
        ResourceCompilerConfiguration.getInstance(myProject).isResourceFile(file)) {
        continue;
      }

      ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
      Module module = rootManager.getFileIndex().getModuleForFile(file);
      if (module != null && ModuleUtilCore.getExtension(module, GroovyModuleExtension.class) != null) {
        modules.add(module);
      }
    }

    Set<Module> noJdkModules = new HashSet<>();
    for (Module module : modules) {
      Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
      if (sdk == null) {
        noJdkModules.add(module);
        continue;
      }

      if (!LibrariesUtil.hasGroovySdk(module)) {
        if (!GroovyConfigUtils.getInstance().tryToSetUpGroovyFacetOnTheFly(module)) {
          Messages.showErrorDialog(
            myProject,
            GroovyLocalize.cannotCompileGroovyFilesNoFacet(module.getName()).get(),
            GroovyLocalize.cannotCompile().get()
          );
          ShowSettingsUtil.getInstance().showProjectStructureDialog(
            module.getProject(),
            projectStructureSelector -> projectStructureSelector.selectOrderEntry(module, null)
          );
          return false;
        }
      }
    }

    if (!noJdkModules.isEmpty()) {
      Module[] noJdkArray = noJdkModules.toArray(new Module[noJdkModules.size()]);
      if (noJdkArray.length == 1) {
        Messages.showErrorDialog(
          myProject,
          GroovyLocalize.cannotCompileGroovyFilesNoSdk(noJdkArray[0].getName()).get(),
          GroovyLocalize.cannotCompile().get()
        );
      }
      else {
        StringBuilder modulesList = new StringBuilder();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) {
            modulesList.append(", ");
          }
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(
          myProject,
          GroovyLocalize.cannotCompileGroovyFilesNoSdkMult(modulesList.toString()).get(),
          GroovyLocalize.cannotCompile().get()
        );
      }
      return false;
    }

    GroovyCompilerConfiguration configuration = GroovyCompilerConfiguration.getInstance(myProject);
    if (!configuration.transformsOk && needTransformCopying(compileScope)) {
        int result = Messages.showYesNoDialog(myProject,
          "You seem to have global Groovy AST transformations defined in your project,\n" +
            "but they won't be applied to your code because they are not marked as compiler resources.\n" +
            "Do you want to add them to compiler resource list?\n" +
            "(you can do it yourself later in Settings | Compiler | Resource patterns)",
          "AST Transformations Found",
          UIUtil.getQuestionIcon()
        );
      if (result == 0) {
        ResourceCompilerConfiguration.getInstance(myProject).addResourceFilePattern(AST_TRANSFORM_FILE_NAME);
      }
      else {
        configuration.transformsOk = true;
      }
    }

    return true;
  }

  @Override
  public void registerCompilableFileTypes(@Nonnull Consumer<FileType> fileTypeConsumer) {
    fileTypeConsumer.accept(GroovyFileType.INSTANCE);
  }

  private boolean needTransformCopying(CompileScope compileScope) {
    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VirtualFile file : FilenameIndex.getVirtualFilesByName(myProject,
                                                                AST_TRANSFORM_FILE_NAME,
                                                                GlobalSearchScope.projectScope(myProject))) {
      if (compileScope.belongs(file.getUrl()) && index.isInSource(file) && !ResourceCompilerConfiguration
        .getInstance(myProject)
        .isResourceFile(file)) {
        return true;
      }
    }
    return false;
  }

  @Nonnull
  @Override
  public FileType[] getInputFileTypes() {
    return new FileType[]{
      GroovyFileType.INSTANCE,
      JavaClassFileType.INSTANCE
    };
  }

  @Nonnull
  @Override
  public FileType[] getOutputFileTypes() {
    return new FileType[]{JavaClassFileType.INSTANCE};
  }
}
