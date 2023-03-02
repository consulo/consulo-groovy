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

import javax.annotation.Nonnull;

import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchStatement;

/**
 * @author peter
 */
public class GrSwitchBodyFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement psiElement) {
    GrSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(psiElement, GrSwitchStatement.class);
    if (switchStatement == null || switchStatement.getLBrace() != null) return;
    if (!PsiTreeUtil.isAncestor(switchStatement.getCondition(), psiElement, false)) return;

    final Document doc = editor.getDocument();

    PsiElement lBrace = switchStatement.getLBrace();
    if (lBrace != null) return;

    PsiElement eltToInsertAfter = switchStatement.getRParenth();
    String text = "{\n}";
    if (eltToInsertAfter == null) {
      eltToInsertAfter = switchStatement.getCondition();
      text = "){\n}";
    }
    doc.insertString(eltToInsertAfter.getTextRange().getEndOffset(), text);
  }

}
