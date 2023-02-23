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
package org.jetbrains.plugins.groovy.lang;

import consulo.language.Language;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GroovyNamesValidator implements NamesValidator {
  @Override
  public boolean isKeyword(String name, Project project) {
    return GroovyRefactoringUtil.KEYWORDS.contains(name);
  }

  @Override
  public boolean isIdentifier(String name, Project project) {
    return GroovyRefactoringUtil.isCorrectReferenceName(name, project);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
