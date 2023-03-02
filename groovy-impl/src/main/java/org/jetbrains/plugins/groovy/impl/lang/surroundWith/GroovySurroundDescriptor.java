/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.surroundWith;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundDescriptor;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;

import javax.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 22.05.2007
 */
@ExtensionImpl
public class GroovySurroundDescriptor implements SurroundDescriptor {
  private static final Surrounder[] ourSurrounders = new Surrounder[]{
    //statements: like in java
    new IfSurrounder(),
    new IfElseSurrounder(),
    new WhileSurrounder(),
    //there's no do-while in Groovy
    new SurrounderByClosure(),
    new GrBracesSurrounder(),
    //like in Java
    new ForSurrounder(),
    new TryCatchSurrounder(),
    new TryFinallySurrounder(),
    new TryCatchFinallySurrounder(),
    //groovy-specific statements
    new ShouldFailWithTypeStatementsSurrounder(),
    //expressions: like in java
    new ParenthesisExprSurrounder(),
    new NotAndParenthesesSurrounder(),
    new TypeCastSurrounder(),

    //groovy-specific
    new WithStatementsSurrounder(),

    new IfExprSurrounder(),
    new IfElseExprSurrounder(),
    new WhileExprSurrounder(),
    new WithExprSurrounder(),
  };

  @Nonnull
  public Surrounder[] getSurrounders() {
    return ourSurrounders;
  }

  @Override
  public boolean isExclusive() {
    return false;
  }

  @Nonnull
  public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
    return GroovyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset, true);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
