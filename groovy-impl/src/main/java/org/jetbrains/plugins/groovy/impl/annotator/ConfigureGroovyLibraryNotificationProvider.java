/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.annotator;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.IndexNotReadyException;
import consulo.compiler.CompilerManager;
import consulo.component.ProcessCanceledException;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.FileEditor;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class ConfigureGroovyLibraryNotificationProvider implements EditorNotificationProvider {
  private final Project myProject;

  private final Set<FileType> supportedFileTypes;

  @Inject
  public ConfigureGroovyLibraryNotificationProvider(Project project) {
    myProject = project;

    supportedFileTypes = new HashSet<>();
    supportedFileTypes.add(GroovyFileType.GROOVY_FILE_TYPE);

    for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensionList()) {
      Collections.addAll(supportedFileTypes, configNotification.getFrameworkFileTypes());
    }
  }

  @Nonnull
  @Override
  public String getId() {
    return "configure-groovy-library";
  }

  @RequiredReadAction
  @Nullable
  @Override
  public EditorNotificationBuilder buildNotification(@Nonnull VirtualFile file,
                                                     @Nonnull FileEditor fileEditor,
                                                     @Nonnull Supplier<EditorNotificationBuilder> supplier) {
    try {
      if (!supportedFileTypes.contains(file.getFileType())) {
        return null;
      }
      // do not show the panel for Gradle build scripts
      // expecting groovy library to always be available at the gradle distribution
      if (StringUtil.endsWith(file.getName(), ".gradle")) {
        return null;
      }
      if (CompilerManager.getInstance(myProject).isExcludedFromCompilation(file)) {
        return null;
      }

      final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
      if (module == null) {
        return null;
      }

      if (isMavenModule(module)) {
        return null;
      }

      for (GroovyFrameworkConfigNotification configNotification : GroovyFrameworkConfigNotification.EP_NAME.getExtensions()) {
        if (configNotification.hasFrameworkStructure(module)) {
          if (!configNotification.hasFrameworkLibrary(module)) {
            return configNotification.createConfigureNotificationPanel(module, supplier);
          }
          return null;
        }
      }
    }
    catch (ProcessCanceledException | IndexNotReadyException ignored) {
    }

    return null;
  }

  private static boolean isMavenModule(@Nonnull Module module) {
    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild("pom.xml") != null) {
        return true;
      }
    }

    return false;
  }

}
