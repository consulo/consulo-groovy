/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.completion.handlers;

import com.intellij.java.impl.codeInsight.generation.GenerateMembersUtil;
import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;

import java.util.List;

public class GroovyMethodOverrideHandler implements InsertHandler<LookupElement> {

  private final PsiClass myPsiClass;

  public GroovyMethodOverrideHandler(PsiClass aClass) {
    this.myPsiClass = aClass;
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
    PsiMethod method = (PsiMethod)item.getObject();
    List<PsiMethod> prototypes = OverrideImplementUtil.overrideOrImplementMethod(myPsiClass, method, false);
    context.commitDocument();
    GenerateMembersUtil.insertMembersAtOffset(context.getFile(), context.getStartOffset(),
                                              OverrideImplementUtil.convert2GenerationInfos(prototypes));
  }
}
