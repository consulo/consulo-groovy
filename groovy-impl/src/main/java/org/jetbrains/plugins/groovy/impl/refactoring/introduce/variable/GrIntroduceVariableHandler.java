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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.RangeMarker;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.function.Consumer;

/**
 * Created by Max Medvedev on 10/29/13
 */
public class GrIntroduceVariableHandler extends GrIntroduceHandlerBase<GroovyIntroduceVariableSettings, GrControlFlowOwner> {
  public static final String DUMMY_NAME = "________________xxx_________________";
  protected static final LocalizeValue REFACTORING_NAME = GroovyRefactoringLocalize.introduceVariableTitle();
  private RangeMarker myPosition = null;

  @Nonnull
  @Override
  @RequiredReadAction
  protected GrControlFlowOwner[] findPossibleScopes(GrExpression selectedExpr,
                                                    GrVariable variable,
                                                    StringPartInfo stringPartInfo,
                                                    Editor editor) {
    // Get container element
    GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(stringPartInfo != null ? stringPartInfo.getLiteral() : selectedExpr);
    if (scope == null) {
      throw new GrRefactoringError(GroovyRefactoringLocalize.refactoringIsNotSupportedInTheCurrentContext0(REFACTORING_NAME));
    }
    if (!GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(scope)) {
      throw new GrRefactoringError(GroovyRefactoringLocalize.refactoringIsNotSupportedInTheCurrentContext0(REFACTORING_NAME));
    }
    return new GrControlFlowOwner[]{scope};
  }

  @Override
  protected void checkExpression(@Nonnull GrExpression selectedExpr) {
    // Cannot perform refactoring in parameter default values

    PsiElement parent = selectedExpr.getParent();
    while (!(parent == null || parent instanceof GroovyFileBase || parent instanceof GrParameter)) {
      parent = parent.getParent();
    }

    if (checkInFieldInitializer(selectedExpr)) {
      throw new GrRefactoringError(GroovyRefactoringLocalize.refactoringIsNotSupportedInTheCurrentContext());
    }

    if (parent instanceof GrParameter) {
      throw new GrRefactoringError(GroovyRefactoringLocalize.refactoringIsNotSupportedInMethodParameters());
    }
  }

  @Override
  protected void checkVariable(@Nonnull GrVariable variable) throws GrRefactoringError {
    throw new GrRefactoringError((String) null);
  }

  @Override
  protected void checkStringLiteral(@Nonnull StringPartInfo info) throws GrRefactoringError {
    //todo
  }

  @Override
  protected void checkOccurrences(@Nonnull PsiElement[] occurrences) {
    //nothing to do
  }

  private static boolean checkInFieldInitializer(@Nonnull GrExpression expr) {
    PsiElement parent = expr.getParent();
    if (parent instanceof GrClosableBlock) {
      return false;
    }
    if (parent instanceof GrField field && expr == field.getInitializerGroovy()) {
      return true;
    }
    if (parent instanceof GrExpression expression) {
      return checkInFieldInitializer(expression);
    }
    return false;
  }

  /**
   * Inserts new variable declarations and replaces occurrences
   */
  @Override
  @RequiredWriteAction
  public GrVariable runRefactoring(@Nonnull GrIntroduceContext context,
                                   @Nonnull GroovyIntroduceVariableSettings settings) {
    // Generating variable declaration

    GrVariable insertedVar = processExpression(context, settings);
    moveOffsetToPositionMarker(context.getEditor());
    return insertedVar;
  }

  private void moveOffsetToPositionMarker(Editor editor) {
    if (editor != null && getPositionMarker() != null) {
      editor.getSelectionModel().removeSelection();
      editor.getCaretModel().moveToOffset(getPositionMarker().getEndOffset());
    }
  }

  @Override
  @RequiredUIAccess
  protected GrInplaceVariableIntroducer getIntroducer(@Nonnull GrIntroduceContext context, OccurrencesChooser.ReplaceChoice choice) {
    final SimpleReference<GrIntroduceContext> contextRef = SimpleReference.create(context);

    if (context.getStringPart() != null) {
      extractStringPart(contextRef);
    }

    context = contextRef.get();

    GrStatement anchor = findAnchor(context, choice == OccurrencesChooser.ReplaceChoice.ALL);

    if (anchor.getParent() instanceof GrControlStatement) {
      addBraces(anchor, contextRef);
    }

    return new GrInplaceVariableIntroducer(getRefactoringName().get(), choice, contextRef.get()) {
      @Override
      @RequiredWriteAction
      protected GrVariable runRefactoring(GrIntroduceContext context,
                                          GroovyIntroduceVariableSettings settings,
                                          boolean processUsages) {
        if (processUsages) {
          return processExpression(context, settings);
        }
        else {
          return addVariable(context, settings);
        }
      }

      @Override
      protected void performPostIntroduceTasks() {
        super.performPostIntroduceTasks();
        moveOffsetToPositionMarker(contextRef.get().getEditor());
      }
    };
  }

  @RequiredWriteAction
  private static GrVariable addVariable(@Nonnull GrIntroduceContext context,
                                        @Nonnull GroovyIntroduceVariableSettings settings) {
    GrStatement anchor = findAnchor(context, settings.replaceAllOccurrences());
    PsiElement parent = anchor.getParent();
    assert parent instanceof GrStatementOwner;
    GrVariableDeclaration generated = generateDeclaration(context, settings);
    GrStatement declaration = ((GrStatementOwner)parent).addStatementBefore(generated, anchor);
    declaration = (GrStatement)JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences
      (declaration);

    return ((GrVariableDeclaration)declaration).getVariables()[0];
  }

  @Override
  protected void showScopeChooser(GrControlFlowOwner[] scopes, Consumer<GrControlFlowOwner> callback, Editor editor) {
    //todo do nothing right now
  }

  @Nonnull
  @RequiredReadAction
  private static GrVariableDeclaration generateDeclaration(@Nonnull GrIntroduceContext context,
                                                           @Nonnull GroovyIntroduceVariableSettings settings) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    String[] modifiers = settings.isDeclareFinal() ? new String[]{PsiModifier.FINAL} : null;

    GrVariableDeclaration declaration = factory.createVariableDeclaration(modifiers, "foo", settings.getSelectedType(), settings.getName());

    generateInitializer(context, declaration.getVariables()[0]);
    return declaration;
  }

  @Nonnull
  @RequiredWriteAction
  private GrVariable processExpression(@Nonnull GrIntroduceContext context,
                                       @Nonnull GroovyIntroduceVariableSettings settings) {
    GrVariableDeclaration varDecl = generateDeclaration(context, settings);

    if (context.getStringPart() != null) {
      GrExpression ref = context.getStringPart().replaceLiteralWithConcatenation(DUMMY_NAME);
      return doProcessExpression(context, settings, varDecl, new PsiElement[]{ref}, ref, true);
    }
    else {
      GrExpression expression = context.getExpression();
      assert expression != null;
      return doProcessExpression(context, settings, varDecl, context.getOccurrences(), expression, true);
    }
  }

  private GrVariable doProcessExpression(@Nonnull final GrIntroduceContext context,
                                         @Nonnull GroovyIntroduceVariableSettings settings,
                                         @Nonnull GrVariableDeclaration varDecl,
                                         @Nonnull PsiElement[] elements,
                                         @Nonnull GrExpression expression,
                                         boolean processUsages) {
    return new GrIntroduceLocalVariableProcessor(context, settings, elements, expression, processUsages) {
      @Override
      @RequiredReadAction
      protected void refreshPositionMarker(PsiElement e) {
        GrIntroduceVariableHandler.this.refreshPositionMarker(context.getEditor().getDocument().createRangeMarker(e.getTextRange()));
      }
    }.processExpression(varDecl);
  }

  @Nonnull
  @RequiredReadAction
  private static GrExpression generateInitializer(@Nonnull GrIntroduceContext context, @Nonnull GrVariable variable) {
    GrExpression initializer =
      context.getStringPart() != null ? context.getStringPart().createLiteralFromSelected() : context.getExpression();
    GrExpression dummyInitializer = variable.getInitializerGroovy();
    assert dummyInitializer != null;
    return dummyInitializer.replaceWithExpression(initializer, true);
  }

  void refreshPositionMarker(RangeMarker marker) {
    myPosition = marker;
  }

  private RangeMarker getPositionMarker() {
    return myPosition;
  }

  @Nonnull
  @Override
  protected LocalizeValue getRefactoringName() {
    return REFACTORING_NAME;
  }

  @Nonnull
  @Override
  protected String getHelpID() {
    return HelpID.INTRODUCE_VARIABLE;
  }

  @Override
  @Nonnull
  protected GroovyIntroduceVariableDialog getDialog(@Nonnull GrIntroduceContext context) {
    GroovyVariableValidator validator = new GroovyVariableValidator(context);
    return new GroovyIntroduceVariableDialog(context, validator);
  }
}
