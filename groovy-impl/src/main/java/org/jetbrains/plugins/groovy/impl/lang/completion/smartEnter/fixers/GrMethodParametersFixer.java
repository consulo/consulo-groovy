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
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.impl.lang.completion.smartEnter.GroovySmartEnterProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 08.08.2008
 */
public class GrMethodParametersFixer extends SmartEnterProcessorWithFixers.Fixer<GroovySmartEnterProcessor> {
  @Override
  public void apply(@Nonnull Editor editor, @Nonnull GroovySmartEnterProcessor processor, @Nonnull PsiElement psiElement) {
    if (psiElement instanceof GrParameterList && psiElement.getParent() instanceof GrMethod) {
      PsiElement rParenth = psiElement.getNextSibling();
      if (rParenth == null) return;

//      [todo] ends with comma
      if (! ")".equals(rParenth.getText())) {
        int offset;
        GrParameterList list = (GrParameterList) psiElement;
        final GrParameter[] params = list.getParameters();
        if (params == null || params.length == 0) {
          offset = list.getTextRange().getStartOffset() + 1;
        } else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
