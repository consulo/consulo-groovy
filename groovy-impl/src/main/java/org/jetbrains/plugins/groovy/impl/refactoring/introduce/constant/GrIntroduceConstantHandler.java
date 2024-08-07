/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.constant;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.*;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.introduce.*;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantHandler extends GrIntroduceFieldHandlerBase<GrIntroduceConstantSettings> {
  public static final String REFACTORING_NAME = "Introduce Constant";

  @Nonnull
  @Override
  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Nonnull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  @Override
  protected void checkExpression(@Nonnull GrExpression selectedExpr) {
    GrVariable variable = GrIntroduceHandlerBase.resolveLocalVar(selectedExpr);
    if (variable != null) {
      checkVariable(variable);
    }
    else {
      selectedExpr.accept(new ConstantChecker(selectedExpr, selectedExpr));
    }
  }

  @Override
  protected void checkVariable(@Nonnull GrVariable variable) throws GrRefactoringError {
    final GrExpression initializer = variable.getInitializerGroovy();
    if (initializer == null) {
      throw new GrRefactoringError(RefactoringBundle.message("variable.does.not.have.an.initializer",
                                                             variable.getName()));
    }
    checkExpression(initializer);
  }

  @Override
  protected void checkStringLiteral(@Nonnull StringPartInfo info) throws GrRefactoringError {
    //todo
  }

  @Override
  protected void checkOccurrences(@Nonnull PsiElement[] occurrences) {
    if (hasLhs(occurrences)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.variable.is.used.for.write"));
    }
  }

  @Nullable
  public static PsiClass findContainingClass(GrIntroduceContext context) {
    return (PsiClass)context.getScope();
  }

  @Nonnull
  @Override
  protected GrIntroduceDialog<GrIntroduceConstantSettings> getDialog(@Nonnull GrIntroduceContext context) {
    return new GrIntroduceConstantDialog(context, findContainingClass(context));
  }

  @Override
  public GrField runRefactoring(@Nonnull GrIntroduceContext context, @Nonnull GrIntroduceConstantSettings settings) {
    return new GrIntroduceConstantProcessor(context, settings).run();
  }

  @Override
  protected GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> getIntroducer(@Nonnull GrIntroduceContext
                                                                                     context,
                                                                                   @Nonnull OccurrencesChooser.ReplaceChoice choice) {
    final Ref<GrIntroduceContext> contextRef = Ref.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    return new GrInplaceConstantIntroducer(contextRef.get(), choice);
  }

  @Nonnull
  @Override
  protected Map<OccurrencesChooser.ReplaceChoice, List<Object>> getOccurrenceOptions(@Nonnull GrIntroduceContext
                                                                                       context) {
    HashMap<OccurrencesChooser.ReplaceChoice, List<Object>> map = new LinkedHashMap<>();

    GrVariable localVar = resolveLocalVar(context);
    if (localVar != null) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.<Object>asList(context.getOccurrences()));
      return map;
    }

    if (context.getExpression() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getExpression()));
    }
    else if (context.getStringPart() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getStringPart()));
    }

    PsiElement[] occurrences = context.getOccurrences();
    if (occurrences.length > 1) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.<Object>asList(occurrences));
    }
    return map;
  }

  private static class ConstantChecker extends GroovyRecursiveElementVisitor {
    private final PsiElement scope;
    private final GrExpression expr;

    @Override
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      final PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiVariable) {
        if (!isStaticFinalField((PsiVariable)resolved)) {
          if (expr instanceof GrClosableBlock) {
            if (!PsiTreeUtil.isContextAncestor(scope, resolved, true)) {
              throw new GrRefactoringError(GroovyRefactoringBundle.message("closure.uses.external" +
                                                                             ".variables"));
            }
          }
          else {
            throw new GrRefactoringError(RefactoringBundle.message("selected.expression.cannot.be.a" +
                                                                     ".constant.initializer"));
          }
        }
      }
      else if (resolved instanceof PsiMethod && ((PsiMethod)resolved).getContainingClass() != null) {
        final GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null || (qualifier instanceof GrReferenceExpression && ((GrReferenceExpression)
          qualifier).resolve() instanceof PsiClass)) {
          if (!((PsiMethod)resolved).hasModifierProperty(PsiModifier.STATIC)) {
            throw new GrRefactoringError(RefactoringBundle.message("selected.expression.cannot.be.a" +
                                                                     ".constant.initializer"));
          }
        }
      }
    }

    private static boolean isStaticFinalField(PsiVariable var) {
      return var instanceof PsiField && var.hasModifierProperty(PsiModifier.FINAL) && var.hasModifierProperty
        (PsiModifier.STATIC);
    }

    @Override
    public void visitClosure(GrClosableBlock closure) {
      if (closure == expr) {
        super.visitClosure(closure);
      }
      else {
        closure.accept(new ConstantChecker(closure, scope));
      }
    }

    private ConstantChecker(GrExpression expr, PsiElement expressionScope) {
      scope = expressionScope;
      this.expr = expr;
    }
  }
}
