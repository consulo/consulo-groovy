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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.List;

public class GroovyMethodParameterUnwrapper extends GroovyUnwrapper {
  public GroovyMethodParameterUnwrapper() {
    super("");
  }

  @Override
  public String getDescription(PsiElement e) {
    String text = e.getText();
    if (text.length() > 20) text = text.substring(0, 17) + "...";
    return CodeInsightBundle.message("unwrap.with.placeholder", text);
  }

  public boolean isApplicableTo(PsiElement e) {
    return (e instanceof GrExpression) && e.getParent() instanceof GrArgumentList;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement methodCall = element.getParent().getParent();
    context.extractElement(element, methodCall);

    context.deleteExactly(methodCall);
  }
}
