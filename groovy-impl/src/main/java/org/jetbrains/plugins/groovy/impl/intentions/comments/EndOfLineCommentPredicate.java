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

import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import consulo.language.ast.IElementType;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

class EndOfLineCommentPredicate implements PsiElementPredicate {

  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof PsiComment)) {
      return false;
    }
    if (element instanceof PsiDocComment) {
      return false;
    }
    final PsiComment comment = (PsiComment) element;
    final IElementType type = comment.getTokenType();
    return GroovyTokenTypes.mSL_COMMENT.equals(type);
  }
}
