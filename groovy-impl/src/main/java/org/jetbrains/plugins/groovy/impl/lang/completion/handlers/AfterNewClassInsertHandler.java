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

package org.jetbrains.plugins.groovy.impl.lang.completion.handlers;

import com.intellij.java.impl.codeInsight.completion.ConstructorInsertHandler;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionFeatures;
import com.intellij.java.language.psi.PsiAnonymousClass;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiModifier;
import consulo.codeEditor.Editor;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupItem;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import javax.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class AfterNewClassInsertHandler implements InsertHandler<LookupItem<PsiClassType>>
{
  private static final Logger LOG = Logger.getInstance(AfterNewClassInsertHandler.class);

  private final PsiClassType myClassType;
  private final boolean myTriggerFeature;

  public AfterNewClassInsertHandler(PsiClassType classType, boolean triggerFeature) {
    myClassType = classType;
    myTriggerFeature = triggerFeature;
  }

  @Override
  public void handleInsert(final InsertionContext context, LookupItem<PsiClassType> item) {
    final PsiClassType.ClassResolveResult resolveResult = myClassType.resolveGenerics();
    final PsiClass psiClass = resolveResult.getElement();
    if (psiClass == null || !psiClass.isValid()) {
      return;
    }

    GroovyPsiElement place =
      PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GroovyPsiElement.class, false);
    boolean hasParams = place != null && GroovyCompletionUtil.hasConstructorParameters(psiClass, place);
    if (myTriggerFeature) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.AFTER_NEW);
    }

    if (hasParams) {
      ParenthesesInsertHandler.WITH_PARAMETERS.handleInsert(context, item);
    }
    else {
      ParenthesesInsertHandler.NO_PARAMETERS.handleInsert(context, item);
    }

    shortenRefsInGenerics(context);
    if (hasParams) {
      AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), null);
    }

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    if (psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final Editor editor = context.getEditor();
      final int offset = context.getTailOffset();
      editor.getDocument().insertString(offset, " {}");
      editor.getCaretModel().moveToOffset(offset + 2);

      context.setLaterRunnable(generateAnonymousBody(editor, context.getFile()));

    }
  }

  private static void shortenRefsInGenerics(InsertionContext context) {
    int offset = context.getStartOffset();

    final String text = context.getDocument().getText();
    while (text.charAt(offset) != '<' && text.charAt(offset) != '(') {
      offset++;
    }
    if (text.charAt(offset) == '<') {
      GroovyCompletionUtil.shortenReference(context.getFile(), offset);
    }
  }

  @Nullable
  private static Runnable generateAnonymousBody(final Editor editor, final PsiFile file) {
    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiElement parent = element.getParent().getParent();
    if (!(parent instanceof PsiAnonymousClass)) return null;

    return ConstructorInsertHandler.genAnonymousBodyFor((PsiAnonymousClass)parent, editor, file, project);
  }

}
