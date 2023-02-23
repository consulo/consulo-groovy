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
package org.jetbrains.plugins.groovy.lang.resolve;

import javax.annotation.Nonnull;

import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.content.ProjectFileIndex;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.ResolveScopeProvider;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author Max Medvedev
 */
public class GroovyResolveScopeProvider extends ResolveScopeProvider {
  @Override
  public GlobalSearchScope getResolveScope(@Nonnull VirtualFile file, Project project) {
    if (file.getFileType() != GroovyFileType.GROOVY_FILE_TYPE) return null;

    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = projectFileIndex.getModuleForFile(file);

    if (module == null) return null; //groovy files are only in modules

    boolean includeTests = projectFileIndex.isInTestSourceContent(file) || !projectFileIndex.isInSourceContent(file);
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);

    final PsiFile psi = PsiManager.getInstance(project).findFile(file);
    if (psi instanceof GroovyFile && ((GroovyFile)psi).isScript()) {
      return GroovyScriptUtil.getScriptType((GroovyFile)psi).patchResolveScope((GroovyFile)psi, scope);
    }
    else {
      return scope;
    }
  }
}
