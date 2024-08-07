/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.fixers;

import jakarta.annotation.Nonnull;

import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;

/**
 * @author Maxim.Medvedev
 */
public class GrListFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement psiElement) {
    if (psiElement instanceof GrListOrMap) {
      final PsiElement brack = ((GrListOrMap)psiElement).getRBrack();
      if (brack == null) {
        editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "]");
      }
    }
  }
}
