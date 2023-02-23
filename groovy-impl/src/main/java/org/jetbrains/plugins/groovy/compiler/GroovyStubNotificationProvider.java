/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.FileEditor;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * @author peter
 */
public class GroovyStubNotificationProvider implements EditorNotificationProvider {
  static final String GROOVY_STUBS = "groovyStubs";
  private final Project myProject;

  public GroovyStubNotificationProvider(Project project) {
    myProject = project;
  }

  @Nonnull
  @Override
  public String getId() {
    return "groovy.stub.notification";
  }

  @Nullable
  @VisibleForTesting
  @RequiredReadAction
  public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
    final String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
    final int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
    if (stubs < 0 || stubs >= components.length - 3) {
      return null;
    }

    final String moduleName = components[stubs + 1];
    final Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
    if (module == null) {
      return null;
    }

    final String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 3, components.length), ".");
    return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
  }

  private static EditorNotificationBuilder decorateStubFile(final VirtualFile file,
                                                            final Project project,
                                                            EditorNotificationBuilder builder) {
    builder.withText(LocalizeValue.localizeTODO("This stub is generated for Groovy class to make Groovy-Java cross-compilation possible"));
    builder.withAction(LocalizeValue.localizeTODO("Go to the Groovy class"), (c) -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass
        original = findClassByStub(project, file);
      if (original != null) {
        original.navigate(true);
      }
    }));
    builder.withAction(LocalizeValue.localizeTODO("Exclude from stub generation"), (c) -> DumbService.getInstance(project).withAlternativeResolveEnabled(() -> {
      final PsiClass psiClass = findClassByStub(project, file);
      if (psiClass != null) {
        ExcludeFromStubGenerationAction.doExcludeFromStubGeneration(psiClass.getContainingFile());
      }
    }));
    return builder;
  }

  @RequiredReadAction
  @Nullable
  @Override
  public EditorNotificationBuilder buildNotification(@Nonnull VirtualFile file,
                                                     @Nonnull FileEditor fileEditor,
                                                     @Nonnull Supplier<EditorNotificationBuilder> supplier) {
    if (file.getName().endsWith(".java") && file.getPath().contains(GROOVY_STUBS)) {
      final PsiClass psiClass = findClassByStub(myProject, file);
      if (psiClass != null) {
        return decorateStubFile(file, myProject, supplier.get());
      }
    }
    return null;
  }
}
