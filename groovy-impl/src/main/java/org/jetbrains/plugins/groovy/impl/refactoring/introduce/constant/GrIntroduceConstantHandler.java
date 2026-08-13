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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceConstantHandler extends GrIntroduceFieldHandlerBase<GrIntroduceConstantSettings> {
  public static final LocalizeValue REFACTORING_NAME = LocalizeValue.localizeTODO("Introduce Constant");

  @Nonnull
  @Override
  protected LocalizeValue getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Nonnull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_CONSTANT;
  }

  @Override
  @RequiredReadAction
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
  @RequiredReadAction
  protected void checkVariable(@Nonnull GrVariable variable) throws GrRefactoringError {
    GrExpression initializer = variable.getInitializerGroovy();
    if (initializer == null) {
      throw new GrRefactoringError(RefactoringLocalize.variableDoesNotHaveAnInitializer(variable.getName()));
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
      throw new GrRefactoringError(GroovyRefactoringLocalize.selectedVariableIsUsedForWrite());
    }
  }

  @Nullable
  public static PsiClass findContainingClass(GrIntroduceContext context) {
    return (PsiClass)context.getScope();
  }

  @Nonnull
  @Override
  @RequiredUIAccess
  protected GrIntroduceDialog<GrIntroduceConstantSettings> getDialog(@Nonnull GrIntroduceContext context) {
    return new GrIntroduceConstantDialog(context, findContainingClass(context));
  }

  @Override
  @RequiredWriteAction
  public GrField runRefactoring(@Nonnull GrIntroduceContext context, @Nonnull GrIntroduceConstantSettings settings) {
    return new GrIntroduceConstantProcessor(context, settings).run();
  }

  @Override
  @RequiredUIAccess
  protected GrAbstractInplaceIntroducer<GrIntroduceConstantSettings> getIntroducer(
    @Nonnull GrIntroduceContext context,
    @Nonnull OccurrencesChooser.ReplaceChoice choice
  ) {
    SimpleReference<GrIntroduceContext> contextRef = SimpleReference.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    return new GrInplaceConstantIntroducer(contextRef.get(), choice);
  }

  @Nonnull
  @Override
  @RequiredReadAction
  protected Map<OccurrencesChooser.ReplaceChoice, List<Object>> getOccurrenceOptions(@Nonnull GrIntroduceContext context) {
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
    @RequiredReadAction
    public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiVariable variable) {
        if (!isStaticFinalField(variable)) {
          if (expr instanceof GrClosableBlock) {
            if (!PsiTreeUtil.isContextAncestor(scope, variable, true)) {
              throw new GrRefactoringError(GroovyRefactoringLocalize.closureUsesExternalVariables());
            }
          }
          else {
            throw new GrRefactoringError(RefactoringLocalize.selectedExpressionCannotBeAConstantInitializer());
          }
        }
      }
      else if (resolved instanceof PsiMethod method && method.getContainingClass() != null) {
        GrExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier == null || (qualifier instanceof GrReferenceExpression refExpr && refExpr.resolve() instanceof PsiClass)) {
          if (!method.isStatic()) {
            throw new GrRefactoringError(RefactoringLocalize.selectedExpressionCannotBeAConstantInitializer());
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
