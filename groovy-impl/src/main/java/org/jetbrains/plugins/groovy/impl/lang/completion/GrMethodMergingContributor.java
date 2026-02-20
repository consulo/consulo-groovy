/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.completion.JavaChainLookupElement;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.codeInsight.completion.JavaMethodMergingContributor;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(id = "grMethodMerger", order = "before methodMerger")
public class GrMethodMergingContributor extends CompletionContributor {
  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(@Nonnull AutoCompletionContext context) {
    CompletionParameters parameters = context.getParameters();

    if (parameters.getCompletionType() != CompletionType.SMART && parameters.getCompletionType() != CompletionType.BASIC) {
      return null;
    }

    boolean needInsertBrace = false;
    boolean needInsertParenth = false;

    LookupElement[] items = context.getItems();
    if (items.length > 1) {
      String commonName = null;
      ArrayList<PsiMethod> allMethods = new ArrayList<PsiMethod>();
      for (LookupElement item : items) {
        Object o = item.getPsiElement();
        if (item.getUserData(JavaCompletionUtil.FORCE_SHOW_SIGNATURE_ATTR) != null || !(o instanceof PsiMethod)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        PsiMethod method = (PsiMethod)o;
        JavaChainLookupElement chain = item.as(JavaChainLookupElement.CLASS_CONDITION_KEY);
        String name = method.getName() + "#" + (chain == null ? "" : chain.getQualifier().getLookupString());

        if (commonName != null && !commonName.equals(name)) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        if (hasOnlyClosureParams(method)) {
          needInsertBrace = true;
        }
        else {
          needInsertParenth = true;
        }

        if (needInsertBrace && needInsertParenth) {
          return AutoCompletionDecision.SHOW_LOOKUP;
        }

        commonName = name;
        allMethods.add(method);
      }
      for (LookupElement item : items) {
        JavaCompletionUtil.putAllMethods(item, allMethods);
      }

      return AutoCompletionDecision.insertItem(JavaMethodMergingContributor.findBestOverload(items));
    }

    return super.handleAutoCompletionPossibility(context);

  }

  private static boolean hasOnlyClosureParams(PsiMethod method) {
    PsiParameter[] params = method.getParameterList().getParameters();
    for (PsiParameter param : params) {
      PsiType type = param.getType();
      if (!TypesUtil.isClassType(type, GroovyCommonClassNames.GROOVY_LANG_CLOSURE)) {
        return false;
      }
    }
    return params.length > 0;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
