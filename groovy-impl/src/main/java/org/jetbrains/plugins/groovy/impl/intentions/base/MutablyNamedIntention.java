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
package org.jetbrains.plugins.groovy.impl.intentions.base;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.codeEditor.Editor;

public abstract class MutablyNamedIntention extends Intention {
  private String text = null;

  protected abstract String getTextForElement(PsiElement element);

  @Nonnull
  public String getText() {
    return text;
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    final PsiElement element = findMatchingElement(file, editor);
    if (element != null) {
      text = getTextForElement(element);
    }
    return element != null;
  }
}
