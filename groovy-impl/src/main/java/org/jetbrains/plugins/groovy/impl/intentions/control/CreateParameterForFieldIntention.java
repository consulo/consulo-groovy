/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.impl.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.java.impl.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.ide.impl.ui.impl.PopupChooserBuilder;
import consulo.language.editor.ui.DefaultPsiElementCellRenderer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.popup.JBPopup;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrChangeSignatureProcessor;
import org.jetbrains.plugins.groovy.impl.refactoring.changeSignature.GrParameterInfo;

import jakarta.annotation.Nonnull;

import javax.swing.*;
import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class CreateParameterForFieldIntention extends Intention {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.intentions.control.CreateParameterForFieldIntention");
  private static final Key<CachedValue<List<GrField>>> FIELD_CANDIDATES = Key.create("Fields.candidates");

  @Nonnull
  @Override
  public String getText() {
    return super.getText();
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, final Project project, final Editor editor)
    throws IncorrectOperationException {
    final List<GrField> candidates = findFieldCandidates(element);
    if (candidates != null) {
      performForConstructor(element, project, editor, candidates);
    }
    else {
      final List<GrMethod> constructors = findConstructorCandidates(element);
      performForField(element, project, editor, constructors);
    }
  }

  private static void performForField(PsiElement element, final Project project, Editor editor, List<GrMethod> constructors) {
    final GrField field = PsiTreeUtil.getParentOfType(element, GrField.class);
    if (constructors.size() == 0) return;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrMethod constructor : constructors) {
        addParameter(field, constructor, project);
      }
      return;
    }

    final JList list = new JBList(constructors.toArray(new GrMethod[constructors.size()]));
    list.setCellRenderer(new MethodCellRenderer(true));

    JBPopup popup = new PopupChooserBuilder(list).setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
                                                   setItemChoosenCallback(new Runnable() {
                                                     public void run() {
                                                       final Object[] selectedValues = list.getSelectedValues();
                                                       Arrays.sort(selectedValues, new Comparator<Object>() {
                                                         @Override
                                                         public int compare(Object o1, Object o2) {
                                                           return ((GrMethod)o2).getParameterList()
                                                                                .getParametersCount() - ((GrMethod)o1).getParameterList()
                                                                                                                      .getParametersCount();
                                                         }
                                                       });
                                                       CommandProcessor.getInstance().executeCommand(project,
                                                                                                     new Runnable() {
                                                                                                       @Override
                                                                                                       public void run() {
                                                                                                         AccessToken accessToken =
                                                                                                           WriteAction.start();
                                                                                                         try {
                                                                                                           for (Object selectedValue : selectedValues) {
                                                                                                             LOG.assertTrue(((GrMethod)selectedValue)
                                                                                                                              .isValid());
                                                                                                             addParameter(field,
                                                                                                                          ((GrMethod)selectedValue),
                                                                                                                          project);
                                                                                                           }
                                                                                                         }
                                                                                                         finally {
                                                                                                           accessToken.finish();
                                                                                                         }
                                                                                                       }
                                                                                                     },
                                                                                                     GroovyIntentionsBundle.message(
                                                                                                       "create.parameter.for.field.intention.name"),
                                                                                                     null);
                                                     }
                                                   }).createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  private static void performForConstructor(PsiElement element, final Project project, Editor editor, List<GrField> candidates) {
    final GrMethod constructor = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (candidates.size() == 0) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      for (GrField candidate : candidates) {
        addParameter(candidate, constructor, project);
      }
      return;
    }
    final JList list = new JBList(candidates.toArray(new GrField[candidates.size()]));
    list.setCellRenderer(new DefaultPsiElementCellRenderer());

    JBPopup popup = new PopupChooserBuilder(list).setTitle(GroovyIntentionsBundle.message("create.parameter.for.field.intention.name")).
      setMovable(true).
                                                   setItemChoosenCallback(new Runnable() {
                                                     public void run() {
                                                       final Object[] selectedValues = list.getSelectedValues();
                                                       CommandProcessor.getInstance().executeCommand(project,
                                                                                                     new Runnable() {
                                                                                                       @Override
                                                                                                       public void run() {
                                                                                                         AccessToken accessToken =
                                                                                                           WriteAction.start();
                                                                                                         try {
                                                                                                           for (Object selectedValue : selectedValues) {
                                                                                                             LOG.assertTrue(((GrField)selectedValue)
                                                                                                                              .isValid());
                                                                                                             addParameter(((GrField)selectedValue),
                                                                                                                          constructor,
                                                                                                                          project);
                                                                                                           }
                                                                                                         }
                                                                                                         finally {
                                                                                                           accessToken.finish();
                                                                                                         }
                                                                                                       }
                                                                                                     },
                                                                                                     GroovyIntentionsBundle.message(
                                                                                                       "create.parameter.for.field.intention.name"),
                                                                                                     null);
                                                     }
                                                   }).createPopup();

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  private static void addParameter(final GrField selectedValue, final GrMethod constructor, final Project project) {
    List<GrParameterInfo> parameters = new ArrayList<GrParameterInfo>();
    GrParameter[] constructorParameters = constructor.getParameters();
    for (int i = 0; i < constructorParameters.length; i++) {
      parameters.add(new GrParameterInfo(constructorParameters[i], i));
    }
    final String[] suggestedNames =
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.PARAMETER, selectedValue.getName(), null, null).names;

    final DefaultGroovyVariableNameValidator nameValidator =
      new DefaultGroovyVariableNameValidator(constructor, Collections.<String>emptyList(), false);
    String parameterName = ContainerUtil.find(suggestedNames, new Condition<String>() {
      @Override
      public boolean value(String name) {
        return nameValidator.validateName(name, false).length() > 0;
      }
    });

    if (parameterName == null) {
      parameterName = nameValidator.validateName(suggestedNames[0], true);
    }
    parameters.add(new GrParameterInfo(parameterName, "null", "", selectedValue.getTypeGroovy(), -1, false));

    PsiClassType[] exceptionTypes = constructor.getThrowsList().getReferencedTypes();
    ThrownExceptionInfo[] thrownExceptionInfos = new ThrownExceptionInfo[exceptionTypes.length];
    for (int i = 0; i < exceptionTypes.length; i++) {
      new JavaThrownExceptionInfo(i, exceptionTypes[i]);
    }

    final GrChangeInfoImpl grChangeInfo =
      new GrChangeInfoImpl(constructor, null, null, constructor.getName(), parameters, thrownExceptionInfos, false);

    final String finalParameterName = parameterName;
    final GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(project, grChangeInfo) {
      @Override
      protected void performRefactoring(UsageInfo[] usages) {
        super.performRefactoring(usages);

        final GrOpenBlock block = constructor.getBlock();
        LOG.assertTrue(block != null);
        final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

        final String text;
        if (StringUtil.equals(selectedValue.getName(), finalParameterName)) {
          text = "this." + selectedValue.getName() + " = " + finalParameterName;
        }
        else {
          text = selectedValue.getName() + " = " + finalParameterName;
        }

        final GrStatement assignment = factory.createStatementFromText(text);
        final GrStatement statement = block.addStatementBefore(assignment, null);
        final GrReferenceExpression ref = (GrReferenceExpression)((GrAssignmentExpression)statement).getLValue();
        if (!PsiManager.getInstance(project).areElementsEquivalent(ref.resolve(), selectedValue)) {
          PsiUtil.qualifyMemberReference(ref, selectedValue, selectedValue.getName());
        }

      }
    };
    processor.run();

  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  static class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
      final List<GrField> candidates = findFieldCandidates(element);
      if (candidates != null && candidates.size() > 0) return true;
      final List<GrMethod> constructors = findConstructorCandidates(element);
      return constructors != null && constructors.size() > 0;
    }
  }

  @Nullable
  private static List<GrField> findFieldCandidates(PsiElement element) {
    final GrMethod constructor = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (constructor == null || !constructor.isConstructor()) return null;
    if (constructor.getBlock() == null) return null;
    if (PsiTreeUtil.isAncestor(constructor.getBlock(), element, false)) {
      return null;
    }
    final PsiClass clazz = constructor.getContainingClass();

    if (!(clazz instanceof GrTypeDefinition)) return null;
    return findCandidatesCached(constructor, (GrTypeDefinition)clazz);
  }

  private static List<GrField> findCandidates(GrMethod constructor, final GrTypeDefinition clazz) {
    final List<GrField> usedFields = new ArrayList<GrField>();
    final GrOpenBlock block = constructor.getBlock();
    if (block == null) {
      return usedFields;
    }

    final PsiManager manager = clazz.getManager();
    block.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);
        final PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof GrField &&
          manager.areElementsEquivalent(((GrField)resolved).getContainingClass(), clazz) &&
          PsiUtil.isAccessedForWriting(referenceExpression)) {
          usedFields.add((GrField)resolved);
        }
      }

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
      }
    });

    List<GrField> fields = new ArrayList<GrField>();
    for (final GrField field : clazz.getFields()) {
      if (field.getInitializerGroovy() != null) continue;
      if (ContainerUtil.find(usedFields, new Condition<PsiField>() {
        @Override
        public boolean value(PsiField o) {
          return manager.areElementsEquivalent(o, field);
        }
      }) == null) {
        fields.add(field);
      }
    }

    return fields;
  }

  private static List<GrField> findCandidatesCached(final GrMethod constructor, final GrTypeDefinition clazz) {
    final CachedValue<List<GrField>> value = constructor.getUserData(FIELD_CANDIDATES);
    if (value != null && value.getValue() != null) return value.getValue();
    final CachedValue<List<GrField>> cachedValue =
      CachedValuesManager.getManager(constructor.getProject()).createCachedValue(new CachedValueProvider<List<GrField>>() {
        @Override
        public Result<List<GrField>> compute() {
          return Result.create(findCandidates(constructor, clazz), PsiModificationTracker.JAVA_STRUCTURE_MODIFICATION_COUNT);
        }
      }, false);
    constructor.putUserData(FIELD_CANDIDATES, cachedValue);
    return cachedValue.getValue();
  }


  @Nullable
  private static List<GrMethod> findConstructorCandidates(PsiElement element) {
    final GrField field = PsiTreeUtil.getParentOfType(element, GrField.class);
    if (field == null) return null;
    return findConstructorCandidates(field, (GrTypeDefinition)field.getContainingClass());
  }

  private static List<GrMethod> findConstructorCandidates(final GrField field, GrTypeDefinition psiClass) {
    final List<GrMethod> result = new ArrayList<GrMethod>();
    final PsiMethod[] constructors = psiClass.getConstructors();
    final PsiManager manager = field.getManager();
    for (PsiMethod constructor : constructors) {
      final List<GrField> fields = findCandidatesCached(((GrMethod)constructor), psiClass);
      if (ContainerUtil.find(fields, new Condition<GrField>() {
        @Override
        public boolean value(GrField grField) {
          return manager.areElementsEquivalent(grField, field);
        }
      }) != null) {
        result.add((GrMethod)constructor);
      }
    }
    return result;
  }
}
