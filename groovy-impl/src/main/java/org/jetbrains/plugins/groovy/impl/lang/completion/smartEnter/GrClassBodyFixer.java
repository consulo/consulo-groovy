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
package org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter;

import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 9/5/13
 */
public class GrClassBodyFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement element)
    throws IncorrectOperationException
  {
    if (element instanceof GrTypeDefinition && ((GrTypeDefinition)element).getBody() == null) {
      Document doc = editor.getDocument();
      doc.insertString(element.getTextRange().getEndOffset(), "{\n}");
    }
  }
}
