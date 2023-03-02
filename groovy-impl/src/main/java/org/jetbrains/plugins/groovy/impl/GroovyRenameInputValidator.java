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
package org.jetbrains.plugins.groovy.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.rename.RenameInputValidator;
import consulo.language.pattern.ElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;

import static consulo.language.pattern.PlatformPatterns.psiElement;

@ExtensionImpl
public class GroovyRenameInputValidator implements RenameInputValidator {
  @Override
  public ElementPattern<? extends PsiElement> getPattern() {
    return psiElement(GrNamedElement.class);
  }

  public boolean isInputValid(String newName, PsiElement element, ProcessingContext context) {
    return GroovyRefactoringUtil.isCorrectReferenceName(newName, element.getProject());
  }
}
