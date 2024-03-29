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
package org.jetbrains.plugins.groovy.impl.unwrap;

import consulo.language.editor.CodeInsightBundle;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSynchronizedStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

public class GroovySynchronizedUnwrapper extends GroovyUnwrapper {
  public GroovySynchronizedUnwrapper() {
    super(CodeInsightBundle.message("unwrap.synchronized"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof GrSynchronizedStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    GrOpenBlock body = ((GrSynchronizedStatement)element).getBody();
    context.extractFromCodeBlock(body, element);

    context.delete(element);
  }
}