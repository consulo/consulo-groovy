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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.language.psi.PsiClassOwner;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.search.PsiShortNamesCache;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.application.AccessToken;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressManager;
import consulo.codeEditor.Editor;
import consulo.ide.impl.psi.util.proximity.PsiProximityComparator;
import consulo.java.analysis.impl.JavaQuickFixBundle;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.undoRedo.CommandProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GroovyStaticImportMethodFix implements IntentionAction
{
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.annotator.intentions.GroovyStaticImportMethodFix");
  private final SmartPsiElementPointer<GrMethodCall> myMethodCall;
  private List<PsiMethod> myCandidates = null;

  public GroovyStaticImportMethodFix(@Nonnull GrMethodCall methodCallExpression) {
    myMethodCall = SmartPointerManager.getInstance(methodCallExpression.getProject()).createSmartPsiElementPointer(methodCallExpression);
  }

  @Nonnull
  public String getText() {
    String text = "Static Import Method";
    if (getCandidates().size() == 1) {
      final int options = PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME;
      text += " '" + PsiFormatUtil.formatMethod(getCandidates().get(0), PsiSubstitutor.EMPTY, options, 0) + "'";
    }
    else {
      text += "...";
    }
    return text;
  }

  @Nonnull
  public String getFamilyName() {
    return getText();
  }

  @Nullable
  private static GrReferenceExpression getMethodExpression(GrMethodCall call) {
    GrExpression result = call.getInvokedExpression();
    return result instanceof GrReferenceExpression ? (GrReferenceExpression)result : null;
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    myCandidates = null;
    return myMethodCall != null &&
           myMethodCall.getElement() != null &&
           myMethodCall.getElement().isValid() &&
           getMethodExpression(myMethodCall.getElement()) != null &&
           getMethodExpression(myMethodCall.getElement()).getQualifierExpression() == null &&
           file.getManager().isInProject(file) &&
           !getCandidates().isEmpty();
  }

  @Nonnull
  private List<PsiMethod> getMethodsToImport() {
    PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myMethodCall.getProject());

    GrMethodCall element = myMethodCall.getElement();
    LOG.assertTrue(element != null);
    GrReferenceExpression reference = getMethodExpression(element);
    LOG.assertTrue(reference != null);
    GrArgumentList argumentList = element.getArgumentList();
    String name = reference.getReferenceName();

    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    if (name == null) return list;
    GlobalSearchScope scope = element.getResolveScope();
    PsiMethod[] methods = cache.getMethodsByNameIfNotMoreThan(name, scope, 20);
    List<PsiMethod> applicableList = new ArrayList<PsiMethod>();
    for (PsiMethod method : methods) {
      ProgressManager.checkCanceled();
      if (JavaCompletionUtil.isInExcludedPackage(method, false)) continue;
      if (!method.hasModifierProperty(PsiModifier.STATIC)) continue;
      PsiFile file = method.getContainingFile();
      if (file instanceof PsiClassOwner
          //do not show methods from default package
          && ((PsiClassOwner)file).getPackageName().length() != 0 && PsiUtil.isAccessible(element, method)) {
        list.add(method);
        if (PsiUtil.isApplicable(PsiUtil.getArgumentTypes(element, true), method, PsiSubstitutor.EMPTY, element, false)) {
          applicableList.add(method);
        }
      }
    }
    List<PsiMethod> result = applicableList.isEmpty() ? list : applicableList;
    Collections.sort(result, new PsiProximityComparator(argumentList));
    return result;
  }

  public void invoke(@Nonnull final Project project, final Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    if (getCandidates().size() == 1) {
      final PsiMethod toImport = getCandidates().get(0);
      doImport(toImport);
    }
    else {
      chooseAndImport(editor);
    }
  }

  private void doImport(final PsiMethod toImport) {
    CommandProcessor.getInstance().executeCommand(toImport.getProject(), new Runnable() {
      public void run() {
        AccessToken accessToken = WriteAction.start();

        try {
          try {
            GrMethodCall element = myMethodCall.getElement();
            if (element != null) {
              getMethodExpression(element).bindToElementViaStaticImport(toImport);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        finally {
          accessToken.finish();
        }
      }
    }, getText(), this);

  }

  private void chooseAndImport(Editor editor) {
    final JList list = new JBList(getCandidates().toArray(new PsiMethod[getCandidates().size()]));
    list.setCellRenderer(new MethodCellRenderer(true));
    new consulo.ide.impl.ui.impl.PopupChooserBuilder(list).
      setTitle(JavaQuickFixBundle.message("static.import.method.choose.method.to.import")).
      setMovable(true).
      setItemChoosenCallback(new Runnable() {
        public void run() {
          PsiMethod selectedValue = (PsiMethod)list.getSelectedValue();
          if (selectedValue == null) return;
          LOG.assertTrue(selectedValue.isValid());
          doImport(selectedValue);
        }
      }).createPopup().
      showInBestPositionFor((consulo.dataContext.DataContext)editor);
  }

  public boolean startInWriteAction() {
    return true;
  }

  private List<PsiMethod> getCandidates() {
    if (myCandidates == null) {
      myCandidates = getMethodsToImport();
    }
    return myCandidates;
  }
}
