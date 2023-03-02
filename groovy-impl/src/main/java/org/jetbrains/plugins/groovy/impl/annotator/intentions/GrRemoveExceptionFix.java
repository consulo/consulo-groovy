/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrDisjunctionTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public class GrRemoveExceptionFix implements IntentionAction {
  private String myText;
  private final boolean myDisjunction;

  public GrRemoveExceptionFix(boolean isDisjunction) {
    myDisjunction = isDisjunction;
    if (isDisjunction) {
      myText = GroovyIntentionsBundle.message("remove.exception");
    }
    else {
      myText = GroovyIntentionsBundle.message("remove.catch.block");
    }
  }

  @Nonnull
  @Override
  public String getText() {
    return myText;
  }

  @Nonnull
  //@Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("try.catch.fix");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myDisjunction && findTypeElementInDisjunction(editor, file) != null || !myDisjunction && findCatch(editor, file) != null;
  }

  @Nullable
  private static GrTypeElement findTypeElementInDisjunction(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement at = file.findElementAt(offset);
    final GrDisjunctionTypeElement disjunction = PsiTreeUtil.getParentOfType(at, GrDisjunctionTypeElement.class);
    if (disjunction == null) return null;
    for (GrTypeElement element : disjunction.getTypeElements()) {
      if (element.getTextRange().contains(offset)) {
        return element;
      }
    }
    return null;
  }

  @Nullable
  private static GrCatchClause findCatch(Editor editor, PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement at = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(at, GrCatchClause.class);
  }


  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (myDisjunction) {
      final GrTypeElement element = findTypeElementInDisjunction(editor, file);
      if (element != null) {
        element.delete();
      }
    }
    else {
      final GrCatchClause aCatch = findCatch(editor, file);
      if (aCatch != null) {
        aCatch.delete();
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
