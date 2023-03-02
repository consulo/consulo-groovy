/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.ide.ServiceManager;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import javax.annotation.Nonnull;

@ServiceAPI(ComponentScope.PROJECT)
public abstract class GroovyCodeStyleManager {
  public static GroovyCodeStyleManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyCodeStyleManager.class);
  }

  @Nonnull
  public abstract GrImportStatement addImport(@Nonnull GroovyFile psiFile,
                                              @Nonnull GrImportStatement statement) throws IncorrectOperationException;

  public abstract void removeImport(@Nonnull GroovyFileBase psiFile,
                                    @Nonnull GrImportStatement importStatement) throws IncorrectOperationException;
}
