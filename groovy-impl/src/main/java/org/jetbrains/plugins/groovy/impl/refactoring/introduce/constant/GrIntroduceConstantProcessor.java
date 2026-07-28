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
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrIntroduceConstantProcessor {
  private final GrIntroduceContext context;
  private final GrIntroduceConstantSettings settings;

  public GrIntroduceConstantProcessor(GrIntroduceContext context, GrIntroduceConstantSettings settings) {

    this.context = context;
    this.settings = settings;
  }

  @Nullable
  @RequiredWriteAction
  public GrField run() {
    PsiClass targetClass = settings.getTargetClass();
    if (targetClass == null) {
      return null;
    }

    if (checkErrors(targetClass)) {
      return null;
    }

    GrVariableDeclaration declaration = addDeclaration(targetClass);
    GrField field = (GrField)declaration.getVariables()[0];

    GrVariable localVar = GrIntroduceHandlerBase.resolveLocalVar(context);
    if (localVar != null) {
      assert localVar.getInitializerGroovy() != null : "initializer should exist: " + localVar.getText();
      GrIntroduceHandlerBase.deleteLocalVar(localVar);

      if (settings.replaceAllOccurrences()) {
        processOccurrences(field);
      }
      else {
        replaceOccurrence(field, localVar.getInitializerGroovy(), isEscalateVisibility());
      }
    }
    else if (context.getStringPart() != null) {
      GrExpression ref = context.getStringPart().replaceLiteralWithConcatenation(field.getName());
      PsiElement element = replaceOccurrence(field, ref, isEscalateVisibility());
      updateCaretPosition(element);
    }
    else if (context.getExpression() != null) {
      if (settings.replaceAllOccurrences()) {
        processOccurrences(field);
      }
      else {
        replaceOccurrence(field, context.getExpression(), isEscalateVisibility());
      }
    }
    return field;
  }

  @RequiredWriteAction
  private void processOccurrences(GrField field) {
    PsiElement[] occurrences = context.getOccurrences();
    GroovyRefactoringUtil.sortOccurrences(occurrences);
    for (PsiElement occurrence : occurrences) {
      replaceOccurrence(field, occurrence, isEscalateVisibility());
    }
  }

  @RequiredUIAccess
  private void updateCaretPosition(PsiElement element) {
    context.getEditor().getCaretModel().moveToOffset(element.getTextRange().getEndOffset());
    context.getEditor().getSelectionModel().removeSelection();
  }

  @RequiredWriteAction
  protected GrVariableDeclaration addDeclaration(PsiClass targetClass) {
    GrVariableDeclaration declaration = createField(targetClass);
    GrVariableDeclaration added;
    if (targetClass instanceof GrEnumTypeDefinition enumTypeDef) {
      GrEnumConstantList enumConstants = enumTypeDef.getEnumConstantList();
      added = (GrVariableDeclaration)targetClass.addAfter(declaration, enumConstants);
    }
    else {
      added = ((GrVariableDeclaration)targetClass.add(declaration));
    }

    JavaCodeStyleManager.getInstance(added.getProject()).shortenClassReferences(added);
    return added;
  }

  @RequiredUIAccess
  protected boolean checkErrors(@Nonnull PsiClass targetClass) {
    String fieldName = settings.getName();
    LocalizeValue errorString = check(targetClass, fieldName);

    if (errorString.isNotEmpty()) {
      CommonRefactoringUtil.showErrorMessage(
        GrIntroduceConstantHandler.REFACTORING_NAME,
        RefactoringLocalize.cannotPerformRefactoringWithReason(errorString),
		HelpID.INTRODUCE_CONSTANT,
        context.getProject()
      );
      return true;
    }

    PsiField oldField = targetClass.findFieldByName(fieldName, true);
    if (oldField != null) {
      LocalizeValue message = RefactoringLocalize.fieldExists(fieldName, oldField.getContainingClass().getQualifiedName());
      int answer = Messages.showYesNoDialog(
        context.getProject(),
        message.get(),
        GrIntroduceConstantHandler.REFACTORING_NAME.get(),
        UIUtil.getWarningIcon()
      );
      if (answer != Messages.YES) {
        return true;
      }
    }
    return false;
  }

  @RequiredReadAction
  private LocalizeValue check(@Nonnull PsiClass targetClass, @Nullable String fieldName) {
    if (!GroovyLanguage.INSTANCE.equals(targetClass.getLanguage())) {
      return GroovyRefactoringLocalize.classLanguageIsNotGroovy();
    }

    if (fieldName == null || fieldName.isEmpty()) {
      return RefactoringLocalize.noFieldNameSpecified();
    }

    else if (!PsiNameHelper.getInstance(context.getProject()).isIdentifier(fieldName)) {
      return RefactoringLocalize.zeroIsNotALegalJavaIdentifier(fieldName);
    }

    if (targetClass instanceof GroovyScriptClass) {
      return GroovyRefactoringLocalize.targetClassMustNotBeScript();
    }

    return LocalizeValue.empty();
  }

  @RequiredWriteAction
  private PsiElement replaceOccurrence(@Nonnull GrField field,
                                       @Nonnull PsiElement occurrence,
                                       boolean escalateVisibility) {
    boolean isOriginal = occurrence == context.getExpression();
    GrReferenceExpression newExpr = createRefExpression(field, occurrence);
    PsiElement replaced = occurrence instanceof GrExpression
      ? ((GrExpression)occurrence).replaceWithExpression(newExpr, false)
      : occurrence.replace(newExpr);
    if (escalateVisibility) {
      PsiUtil.escalateVisibility(field, replaced);
    }
    if (replaced instanceof GrReferenceExpression) {
      GrReferenceAdjuster.shortenReference((GrReferenceExpression)replaced);
    }
    if (isOriginal) {
      updateCaretPosition(replaced);
    }
    return replaced;
  }

  @Nonnull
  private static GrReferenceExpression createRefExpression(@Nonnull GrField field, @Nonnull PsiElement place) {
    PsiClass containingClass = field.getContainingClass();
    assert containingClass != null;
    String qName = containingClass.getQualifiedName();
    String fieldName = field.getName();
    String refText = qName != null && !qName.equals(fieldName) ? qName + "." + fieldName : fieldName;
    return GroovyPsiElementFactory.getInstance(place.getProject()).createReferenceExpressionFromText(refText,
                                                                                                     place);
  }

  @Nonnull
  @RequiredReadAction
  private GrVariableDeclaration createField(PsiClass targetClass) {
    String name = settings.getName();
    PsiType type = settings.getSelectedType();

    String[] modifiers = collectModifiers(targetClass);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    return factory.createFieldDeclaration(modifiers, name, getInitializer(), type);
  }

  @Nonnull
  @RequiredReadAction
  protected GrExpression getInitializer() {
    GrVariable var = GrIntroduceHandlerBase.resolveLocalVar(context);
    GrExpression expression = context.getExpression();

    if (var != null) {
      return var.getInitializerGroovy();
    }
    else if (expression != null) {
      return expression;
    }
    else {
      return context.getStringPart().createLiteralFromSelected();
    }
  }

  @Nonnull
  private String[] collectModifiers(PsiClass targetClass) {
    String modifier = isEscalateVisibility() ? PsiModifier.PRIVATE : settings.getVisibilityModifier();
    List<String> modifiers = new ArrayList<>();
    if (modifier != null && !PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
      modifiers.add(modifier);
    }
    if (!targetClass.isInterface()) {
      modifiers.add(PsiModifier.STATIC);
      modifiers.add(PsiModifier.FINAL);
    }
    return ArrayUtil.toStringArray(modifiers);
  }

  private boolean isEscalateVisibility() {
    return VisibilityUtil.ESCALATE_VISIBILITY.equals(settings.getVisibilityModifier());
  }

}
