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

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

class EndOfLineCommentPredicate implements PsiElementPredicate {
    @Override
    @RequiredReadAction
    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof PsiComment comment)) {
            return false;
        }
        if (comment instanceof PsiDocComment) {
            return false;
        }
        return GroovyTokenTypes.mSL_COMMENT.equals(comment.getTokenType());
    }
}
