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
package org.jetbrains.plugins.groovy.impl.unwrap;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.Set;

public abstract class GroovyElseUnwrapperBase extends GroovyUnwrapper {
  public GroovyElseUnwrapperBase(String description) {
    super(description);
  }

  public boolean isApplicableTo(PsiElement e) {
    return (isElseBlock(e) || isElseKeyword(e)) && isValidConstruct(e);
  }

  private static boolean isElseKeyword(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof GrIfStatement && PsiImplUtil.isLeafElementOfType(e, GroovyTokenTypes.kELSE);
  }

  private static boolean isValidConstruct(PsiElement e) {
    return ((GrIfStatement)e.getParent()).getElseBranch() != null;
  }

  @Override
  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
    PsiElement parent = element.getParent();

    while (parent instanceof GrIfStatement) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    GrStatement elseBranch;

    if (isElseKeyword(element)) {
      elseBranch = ((GrIfStatement)element.getParent()).getElseBranch();
    }
    else {
      elseBranch = (GrStatement)element;
    }

    unwrapElseBranch(elseBranch, element.getParent(), context);
  }

  protected abstract void unwrapElseBranch(GrStatement branch, PsiElement parent, Context context) throws IncorrectOperationException;
}