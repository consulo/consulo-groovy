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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.completion.JavaCompletionFeatures;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.ElementPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 14/05/14
 */
class GrThisSuperCompletionProvider implements CompletionProvider {
  private static final ElementPattern<PsiElement> AFTER_DOT =
    PlatformPatterns.psiElement().afterLeaf(".").withParent(GrReferenceExpression.class);
  private static final String[] THIS_SUPER = {"this", "super"};

  public static void register(CompletionContributor contributor) {
    contributor.extend(CompletionType.BASIC, AFTER_DOT, new GrThisSuperCompletionProvider());
  }

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                             ProcessingContext context,
                             @Nonnull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();

    assert position.getParent() instanceof GrReferenceExpression;
    final GrReferenceExpression refExpr = ((GrReferenceExpression)position.getParent());
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (!(qualifier instanceof GrReferenceExpression)) return;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifier;
    final PsiElement resolved = referenceExpression.resolve();
    if (!(resolved instanceof PsiClass)) return;

    if (parameters.getInvocationCount() > 0 &&
      CompletionUtilCore.shouldShowFeature(parameters, JavaCompletionFeatures.GLOBAL_MEMBER_NAME)) {
      final String shortcut =
        KeymapUtil.getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION));
      if (StringUtil.isNotEmpty(shortcut)) {
        result.addLookupAdvertisement("Pressing " + shortcut + " twice without a class qualifier would show all accessible static methods");
      }
    }

    if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, position, false)) return;

    for (String keyword : THIS_SUPER) {
      result.addElement(LookupElementBuilder.create(keyword));
    }
  }
}
