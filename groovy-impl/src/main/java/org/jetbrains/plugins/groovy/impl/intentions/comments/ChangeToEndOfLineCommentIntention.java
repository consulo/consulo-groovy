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
package org.jetbrains.plugins.groovy.impl.intentions.comments;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;

public class ChangeToEndOfLineCommentIntention extends Intention {

  @Nonnull
  protected PsiElementPredicate getElementPredicate() {
    return new CStyleCommentPredicate();
  }

  public void processIntention(@Nonnull PsiElement element, Project project, Editor editor)
      throws IncorrectOperationException {
    final PsiComment comment = (PsiComment) element;
    final JavaPsiFacade manager = JavaPsiFacade.getInstance(comment.getProject());
    final PsiElement parent = comment.getParent();
    assert parent != null;
    final PsiElementFactory factory = manager.getElementFactory();
    final String commentText = comment.getText();
    final PsiElement whitespace = comment.getNextSibling();
    final String text = commentText.substring(2, commentText.length() - 2);
    final String[] lines = text.split("\n");
    for (int i = lines.length - 1; i >= 1; i--) {
      final PsiComment nextComment =
          factory.createCommentFromText("//" + lines[i].trim() + '\n',
              parent);
      parent.addAfter(nextComment, comment);
      /* if (whitespace != null) {
      final PsiElement newWhiteSpace =
          factory.createWhiteSpaceFromText(whitespace.getText());
      parent.addAfter(newWhiteSpace, comment);
    }  */
    }
    final PsiComment newComment =
        factory.createCommentFromText("//" + lines[0], parent);
    comment.replace(newComment);
  }
}
