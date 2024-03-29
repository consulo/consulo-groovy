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
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class GroovyPsiElementVisitor extends PsiElementVisitor {
  protected GroovyElementVisitor myGroovyElementVisitor;

  public GroovyPsiElementVisitor(GroovyElementVisitor groovyElementVisitor) {
    myGroovyElementVisitor = groovyElementVisitor;
  }

  public void visitElement(PsiElement element) {
    if (element instanceof GroovyPsiElement) {
      ((GroovyPsiElement) element).accept(myGroovyElementVisitor);
    }
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
  }
}
