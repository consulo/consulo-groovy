/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.debugger;

import com.intellij.java.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.java.debugger.engine.evaluation.TextWithImports;
import com.intellij.java.debugger.impl.EditorTextProvider;
import com.intellij.java.debugger.impl.engine.evaluation.TextWithImportsImpl;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiEnumConstant;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;

import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyEditorTextProvider implements EditorTextProvider {
  @Override
  public TextWithImports getEditorText(PsiElement elementAtCaret) {
    String result = "";
    PsiElement element = findExpressionInner(elementAtCaret, true);
    if (element != null) {
      if (element instanceof GrReferenceExpression) {
        final GrReferenceExpression reference = (GrReferenceExpression)element;
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiEnumConstant) {
            final PsiEnumConstant enumConstant = (PsiEnumConstant)resolved;
            final PsiClass enumClass = enumConstant.getContainingClass();
            if (enumClass != null) {
              result = enumClass.getName() + "." + enumConstant.getName();
            }
          }
        }
      }
      if (result.length() == 0) {
        result = element.getText();
      }
    }
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, result);
  }

  @Nullable
  private static PsiElement findExpressionInner(PsiElement element, boolean allowMethodCalls) {
    PsiElement parent = element.getParent();
    if (parent instanceof GrVariable && element == ((GrVariable)parent).getNameIdentifierGroovy()) {
      return element;
    }
    else if (parent instanceof GrReferenceExpression) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof GrCall) {
        parent = pparent;
      }
      if (allowMethodCalls || !GroovyRefactoringUtil.hasSideEffect((GroovyPsiElement)parent)) {
        return parent;
      }
    }

    return null;
  }

  @Override
  public Pair<PsiElement, TextRange> findExpression(PsiElement element, boolean allowMethodCalls) {
    PsiElement expression = findExpressionInner(element, allowMethodCalls);
    if (expression == null) return null;
    return new Pair<PsiElement, TextRange>(expression, expression.getTextRange());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
