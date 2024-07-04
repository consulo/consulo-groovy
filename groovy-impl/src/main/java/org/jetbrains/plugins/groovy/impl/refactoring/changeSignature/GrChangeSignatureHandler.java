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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.impl.refactoring.changeSignature.ChangeSignatureUtil;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.dataContext.DataContext;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.changeSignature.ChangeSignatureHandler;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureHandler implements ChangeSignatureHandler {
  @RequiredUIAccess
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = findTargetMember(file, editor);
    if (element == null) {
      element = dataContext.getData(PsiElement.KEY);
    }
    invokeOnElement(project, editor, element);
  }

  @RequiredUIAccess
  private static void invokeOnElement(Project project, Editor editor, PsiElement element) {
    if (element instanceof PsiMethod method) {
      invoke(method, project);
    }
    else {
      String message =
        RefactoringBundle.getCannotRefactorMessage(GroovyRefactoringBundle.message("error.wrong.caret.position.method.name"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME.get(), HelpID.CHANGE_SIGNATURE);
    }
  }

  @RequiredUIAccess
  public void invoke(@Nonnull final Project project, @Nonnull final PsiElement[] elements, final DataContext dataContext) {
    if (elements.length != 1) {
      return;
    }
    Editor editor = dataContext == null ? null : dataContext.getData(Editor.KEY);
    invokeOnElement(project, editor, elements[0]);
  }

  @Nullable
  @Override
  public String getTargetNotFoundMessage() {
    return null;
  }

  @RequiredUIAccess
  private static void invoke(PsiMethod method, final Project project) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) {
      return;
    }
    if (method instanceof GrReflectedMethod reflectedMethod) {
      method = reflectedMethod.getBaseMethod();
    }

    PsiMethod newMethod = SuperMethodWarningUtil.checkSuperMethod(method, RefactoringLocalize.toRefactor().get());
    if (newMethod == null) {
      return;
    }

    if (!newMethod.equals(method)) {
      ChangeSignatureUtil.invokeChangeSignatureOn(newMethod, project);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, method)) {
      return;
    }

    if (!(method instanceof GrMethod)) {
      return; //todo
    }
    final GrChangeSignatureDialog dialog = new GrChangeSignatureDialog(project, new GrMethodDescriptor((GrMethod)method), true, null);
    dialog.show();
  }

  @Nullable
  @RequiredReadAction
  public PsiElement findTargetMember(PsiFile file, Editor editor) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiElement targetMember = findTargetMember(element);
    if (targetMember != null) {
      return targetMember;
    }

    final PsiReference reference = file.findReferenceAt(editor.getCaretModel().getOffset());
    if (reference != null) {
      return reference.resolve();
    }
    return null;
  }

  @Nullable
  @RequiredReadAction
  public PsiElement findTargetMember(PsiElement element) {
    final GrParameterList parameterList = PsiTreeUtil.getParentOfType(element, GrParameterList.class);
    if (parameterList != null) {
      final PsiElement parent = parameterList.getParent();
      if (parent instanceof PsiMethod) {
        return parent;
      }
    }

    if (element.getParent() instanceof GrMethod method && method.getNameIdentifierGroovy() == element) {
      return element.getParent();
    }
    final GrCall expression = PsiTreeUtil.getParentOfType(element, GrCall.class);
    if (expression != null) {
      return expression.resolveMethod();
    }

    final PsiReference ref = element.getReference();
    if (ref == null) {
      return null;
    }
    return ref.resolve();
  }
}
