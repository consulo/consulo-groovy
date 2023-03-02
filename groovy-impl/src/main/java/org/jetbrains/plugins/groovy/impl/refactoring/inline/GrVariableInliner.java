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
package org.jetbrains.plugins.groovy.impl.refactoring.inline;

import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditorManager;
import consulo.language.editor.refactoring.inline.InlineHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.project.Project;
import consulo.project.ui.wm.WindowManager;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.LOG;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses;

/**
 * @author Max Medvedev
 */
public class GrVariableInliner implements InlineHandler.Inliner {
  private final GrExpression myTempExpr;

  public GrVariableInliner(GrVariable variable, InlineHandler.Settings settings) {
    GrExpression initializer;
    if (settings instanceof InlineLocalVarSettings) {
      initializer = ((InlineLocalVarSettings)settings).getInitializer();
    }
    else {
      initializer = variable.getInitializerGroovy();
      LOG.assertTrue(initializer != null);
    }
    myTempExpr = (GrExpression)skipParentheses(initializer, false);
  }

  @Nullable
  public MultiMap<PsiElement, String> getConflicts(@Nonnull PsiReference reference, @Nonnull PsiElement referenced) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    GrExpression expr = (GrExpression)reference.getElement();
    if (expr.getParent() instanceof GrAssignmentExpression) {
      GrAssignmentExpression parent = (GrAssignmentExpression)expr.getParent();
      if (expr.equals(parent.getLValue())) {
        conflicts.putValue(expr, GroovyRefactoringBundle.message("local.varaible.is.lvalue"));
      }
    }

    if ((referenced instanceof GrAccessorMethod || referenced instanceof GrField) && expr instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)expr).advancedResolve();
      if (resolveResult.getElement() instanceof GrAccessorMethod && !resolveResult.isInvokedOnProperty()) {
        final PsiElement parent = expr.getParent();
        if (!(parent instanceof GrCall && parent instanceof GrExpression)) {
          conflicts.putValue(expr,
                             GroovyRefactoringBundle.message("reference.to.accessor.0.is.used",
                                                             CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(
                                                               (GrAccessorMethod)resolveResult.getElement(),
                                                               PsiSubstitutor.EMPTY,
                                                               PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                               PsiFormatUtilBase.SHOW_TYPE))));
        }
      }
    }

    return conflicts;
  }

  public void inlineUsage(@Nonnull final UsageInfo usage, @Nonnull final PsiElement referenced) {
    inlineReference(usage, referenced, myTempExpr);
  }

  static void inlineReference(UsageInfo usage, PsiElement referenced, GrExpression initializer) {
    if (initializer == null) return;

    GrExpression exprToBeReplaced = (GrExpression)usage.getElement();
    if (exprToBeReplaced == null) return;

    if ((referenced instanceof GrAccessorMethod || referenced instanceof GrField) && exprToBeReplaced instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)exprToBeReplaced).advancedResolve();
      if (resolveResult.getElement() instanceof GrAccessorMethod && !resolveResult.isInvokedOnProperty()) {
        final PsiElement parent = exprToBeReplaced.getParent();
        if (parent instanceof GrCall && parent instanceof GrExpression) {
          exprToBeReplaced = (GrExpression)parent;
        }
        else {
          return;
        }
      }
    }

    GrExpression newExpr = exprToBeReplaced.replaceWithExpression((GrExpression)initializer.copy(), true);
    final Project project = usage.getProject();
    Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    GroovyRefactoringUtil.highlightOccurrences(project, editor, new PsiElement[]{newExpr});
    WindowManager.getInstance().getStatusBar(project).setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }
}
