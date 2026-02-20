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
package org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.fixers;

import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;

public class GrSynchronizedFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement psiElement) {
    GrSynchronizedStatement synchronizedStatement = PsiTreeUtil.getParentOfType(psiElement, GrSynchronizedStatement.class);
    if (synchronizedStatement == null || synchronizedStatement.getBody() != null) return;

    if (!PsiTreeUtil.isAncestor(synchronizedStatement.getMonitor(), psiElement, false)) return;


    Document doc = editor.getDocument();

    PsiElement eltToInsertAfter = synchronizedStatement.getRParenth();
    String text = "{\n}";
    if (eltToInsertAfter == null) {
      eltToInsertAfter = synchronizedStatement.getMonitor();
      text = "){\n}";
    }

    doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
  }
}
