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
package org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class SecondUnsafeCallInspection extends GroovySuppressableInspectionTool {
  @Nonnull
  public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
    return new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      public void visitReferenceExpression(GrReferenceExpression refExpression) {
        checkForSecondUnsafeCall(refExpression, holder);
      }
    });
  }

  private static void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder) {
    checkForSecondUnsafeCall(expression, holder, null);
  }

  private static void checkForSecondUnsafeCall(GrExpression expression, ProblemsHolder holder, @Nullable PsiElement highlightElement) {
    if (highlightElement == null) highlightElement = expression;

    final GrReferenceExpression referenceExpression = (GrReferenceExpression)expression;

    if (GroovyTokenTypes.mDOT.equals(referenceExpression.getDotTokenType())) {
      //        a?.b or a?.b()
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      //        a?.b()
      if (qualifier instanceof GrMethodCallExpression) {
        final GrExpression expression1 = ((GrMethodCallExpression)qualifier).getInvokedExpression();
        //        a?.b
        if (!(expression1 instanceof GrReferenceExpression)) return;

        if (GroovyTokenTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)expression1).getDotTokenType())) {
          holder.registerProblem(highlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
        }
      }
      else
        //        a?.b
        if (qualifier instanceof GrReferenceExpression) {
          if (GroovyTokenTypes.mOPTIONAL_DOT.equals(((GrReferenceExpression)qualifier).getDotTokenType())) {
            holder.registerProblem(highlightElement, GroovyInspectionBundle.message("call.can.throw.npe"), new SecondUnsafeCallQuickFix());
          }
        }
    }
  }

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return "Probable bugs";
  }

  @Nonnull
  @Override
  public String[] getGroupPath() {
    return new String[]{"Groovy", getGroupDisplayName()};
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("second.unsafe.call");
  }

  @NonNls
  @Nonnull
  public String getShortName() {
    return "SecondUnsafeCall";
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}