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

import consulo.codeEditor.Editor;
import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.document.Document;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWhileStatement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.08.2008
 */
public class GrWhileBodyFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement psiElement) {
    if (!(psiElement instanceof GrWhileStatement)) return;
    GrWhileStatement whileStatement = (GrWhileStatement) psiElement;

    Document doc = editor.getDocument();

    PsiElement body = whileStatement.getBody();
    if (body instanceof GrBlockStatement) return;
    if (body != null && GrForBodyFixer.startLine(editor.getDocument(), body) ==
                        GrForBodyFixer.startLine(editor.getDocument(), whileStatement) && whileStatement.getCondition() != null) return;

    PsiElement rParenth = whileStatement.getRParenth();
    assert rParenth != null;

    doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
  }
}
