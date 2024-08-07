/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter;

import com.intellij.java.impl.refactoring.IntroduceParameterRefactoring;
import com.intellij.java.impl.refactoring.introduceParameter.*;
import com.intellij.java.impl.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.java.impl.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.util.AnySupers;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceParameterProcessor extends BaseRefactoringProcessor implements IntroduceParameterData {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.introduce.parameter.GrIntroduceParameterProcessor");

  private final GrIntroduceParameterSettings mySettings;
  private final IntroduceParameterData.ExpressionWrapper myParameterInitializer;

  public GrIntroduceParameterProcessor(GrIntroduceParameterSettings settings) {
    this(settings, createExpressionWrapper(settings));
  }

  public GrIntroduceParameterProcessor(GrIntroduceParameterSettings settings, GrExpressionWrapper expr) {
    super(settings.getProject());
    mySettings = settings;

    myParameterInitializer = expr;
  }

  private static GrExpressionWrapper createExpressionWrapper(GrIntroduceParameterSettings settings) {
    LOG.assertTrue(settings.getToReplaceIn() instanceof GrMethod);
    LOG.assertTrue(settings.getToSearchFor() instanceof PsiMethod);

    final StringPartInfo stringPartInfo = settings.getStringPartInfo();
    GrVariable var = settings.getVar();
    final GrExpression expression = stringPartInfo != null ? stringPartInfo.createLiteralFromSelected() : var !=
      null ? var.getInitializerGroovy() : settings.getExpression();
    return new GrExpressionWrapper(expression);
  }

  @Nonnull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull final UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @Nonnull
      @Override
      public PsiElement[] getElements() {
        return new PsiElement[]{mySettings.getToSearchFor()};
      }

      @Override
      public String getProcessedElementsHeader() {
        return RefactoringBundle.message("introduce.parameter.elements.header");
      }
    };
  }

  @Override
  protected boolean preprocessUsages(@Nonnull Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    if (!mySettings.generateDelegate()) {
      GroovyIntroduceParameterUtil.detectAccessibilityConflicts(mySettings.getExpression(), usagesIn, conflicts,
                                                                mySettings.replaceFieldsWithGetters() != IntroduceParameterRefactoring
                                                                  .REPLACE_FIELDS_WITH_GETTERS_NONE, myProject);
    }

    final GrMethod toReplaceIn = (GrMethod)mySettings.getToReplaceIn();
    if (mySettings.getExpression() != null && !toReplaceIn.hasModifierProperty(PsiModifier.PRIVATE)) {
      final AnySupers anySupers = new AnySupers();
      mySettings.getExpression().accept(anySupers);
      if (anySupers.containsSupers()) {
        for (UsageInfo usageInfo : usagesIn) {
          if (!(usageInfo.getElement() instanceof PsiMethod) && !(usageInfo instanceof InternalUsageInfo)) {
            if (!PsiTreeUtil.isAncestor(toReplaceIn.getContainingClass(), usageInfo.getElement(), false)) {
              conflicts.putValue(mySettings.getExpression(), RefactoringBundle.message("parameter" +
                                                                                         ".initializer.contains.0.but.not.all.calls.to.method.are.in.its.class",
                                                                                       CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)));
              break;
            }
          }
        }
      }
    }

    for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME
      .getExtensions()) {
      processor.findConflicts(this, refUsages.get(), conflicts);
    }

    return showConflicts(conflicts, usagesIn);
  }

  @Nonnull
  @Override
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    final PsiMethod toSearchFor = ((PsiMethod)mySettings.getToSearchFor());

    if (!mySettings.generateDelegate()) {
      Collection<PsiReference> refs = MethodReferencesSearch.search(toSearchFor,
																																		GlobalSearchScope.projectScope(myProject), true).findAll();

      for (PsiReference ref1 : refs) {
        PsiElement ref = ref1.getElement();
        if (ref instanceof PsiMethod && ((PsiMethod)ref).isConstructor()) {
          DefaultConstructorImplicitUsageInfo implicitUsageInfo = new DefaultConstructorImplicitUsageInfo(
            (PsiMethod)ref, ((PsiMethod)ref).getContainingClass(), toSearchFor);
          result.add(implicitUsageInfo);
        }
        else if (ref instanceof PsiClass) {
          if (ref instanceof GrAnonymousClassDefinition) {
            result.add(new ExternalUsageInfo(((GrAnonymousClassDefinition)ref)
                                               .getBaseClassReferenceGroovy()));
          }
          else if (ref instanceof PsiAnonymousClass) {
            result.add(new ExternalUsageInfo(((PsiAnonymousClass)ref).getBaseClassReference()));
          }
          else {
            result.add(new NoConstructorClassUsageInfo((PsiClass)ref));
          }
        }
        else if (!PsiTreeUtil.isAncestor(mySettings.getToReplaceIn(), ref, false)) {
          result.add(new ExternalUsageInfo(ref));
        }
        else {
          result.add(new ChangedMethodCallInfo(ref));
        }
      }
    }

    if (mySettings.replaceAllOccurrences()) {
      if (mySettings.getVar() != null) {
        for (PsiElement element : GrIntroduceHandlerBase.collectVariableUsages(mySettings.getVar(),
                                                                               mySettings.getToReplaceIn())) {
          result.add(new InternalUsageInfo(element));
        }
      }
      else {
        PsiElement[] exprs = GroovyIntroduceParameterUtil.getOccurrences(mySettings);
        for (PsiElement expr : exprs) {
          result.add(new InternalUsageInfo(expr));
        }
      }
    }
    else {
      if (mySettings.getExpression() != null) {
        result.add(new InternalUsageInfo(mySettings.getExpression()));
      }
    }

    Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(toSearchFor, true).findAll();

    for (PsiMethod overridingMethod : overridingMethods) {
      result.add(new UsageInfo(overridingMethod));
    }

    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  @Override
  protected void performRefactoring(@Nonnull UsageInfo[] usages) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    //PsiType initializerType = mySettings.getSelectedType();

    // Changing external occurrences (the tricky part)

    IntroduceParameterUtil.processUsages(usages, this);

    final GrMethod toReplaceIn = (GrMethod)mySettings.getToReplaceIn();
    final PsiMethod toSearchFor = (PsiMethod)mySettings.getToSearchFor();

    final boolean methodsToProcessAreDifferent = toReplaceIn != toSearchFor;
    if (mySettings.generateDelegate()) {
      generateDelegate(toReplaceIn, toSearchFor, methodsToProcessAreDifferent);
    }

    // Changing signature of initial method
    // (signature of myMethodToReplaceIn will be either changed now or have already been changed)
    //LOG.assertTrue(initializerType == null || initializerType.isValid());

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(mySettings.getName(),
                                                                                     toReplaceIn.getBlock());

    processMethodSignature(usages, toReplaceIn, toSearchFor, methodsToProcessAreDifferent);
    processUsages(usages, factory);
    processStringPart();
    processVar();

    fieldConflictsResolver.fix();
  }

  private void processVar() {
    final GrVariable var = mySettings.getVar();
    if (var != null && mySettings.removeLocalVariable()) {
      var.delete();
    }
  }

  private void processStringPart() {
    final StringPartInfo stringPartInfo = mySettings.getStringPartInfo();
    if (stringPartInfo != null) {
      final GrExpression expr = mySettings.getStringPartInfo().replaceLiteralWithConcatenation(mySettings
                                                                                                 .getName());
      final Editor editor = PsiUtilBase.findEditor(expr);
      if (editor != null) {
        editor.getSelectionModel().removeSelection();
        editor.getCaretModel().moveToOffset(expr.getTextRange().getEndOffset());
      }
    }
  }

  private void processUsages(UsageInfo[] usages, GroovyPsiElementFactory factory) {
    // Replacing expression occurrences
    for (UsageInfo usage : usages) {
      if (usage instanceof ChangedMethodCallInfo) {
        PsiElement element = usage.getElement();
        GroovyIntroduceParameterUtil.processChangedMethodCall(element, mySettings, myProject);
      }
      else if (usage instanceof InternalUsageInfo) {
        PsiElement element = usage.getElement();
        if (element == null) {
          continue;
        }
        GrExpression newExpr = factory.createExpressionFromText(mySettings.getName());
        if (element instanceof GrExpression) {
          ((GrExpression)element).replaceWithExpression(newExpr, true);
        }
        else {
          element.replace(newExpr);
        }
      }
    }
  }

  private void processMethodSignature(UsageInfo[] usages,
                                      GrMethod toReplaceIn,
                                      PsiMethod toSearchFor,
                                      boolean methodsToProcessAreDifferent) {
    IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(toReplaceIn), usages, this);
    if (methodsToProcessAreDifferent) {
      IntroduceParameterUtil.changeMethodSignatureAndResolveFieldConflicts(new UsageInfo(toSearchFor), usages,
                                                                           this);
    }
  }

  private void generateDelegate(GrMethod toReplaceIn, PsiMethod toSearchFor, boolean methodsToProcessAreDifferent) {
    GroovyIntroduceParameterUtil.generateDelegate(toReplaceIn, myParameterInitializer, myProject);
    if (methodsToProcessAreDifferent) {
      final GrMethod method = GroovyIntroduceParameterUtil.generateDelegate(toSearchFor, myParameterInitializer,
                                                                            myProject);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.isInterface()) {
        final GrOpenBlock block = method.getBlock();
        if (block != null) {
          block.delete();
        }
      }
    }
  }

  @Override
  protected String getCommandName() {
    return RefactoringBundle.message("introduce.parameter.command", DescriptiveNameUtil.getDescriptiveName
      (mySettings.getToReplaceIn()));
  }

  @Nonnull
  @Override
  public Project getProject() {
    return mySettings.getProject();
  }

  @Override
  public PsiMethod getMethodToReplaceIn() {
    return (PsiMethod)mySettings.getToReplaceIn();
  }

  @Nonnull
  @Override
  public PsiMethod getMethodToSearchFor() {
    return (PsiMethod)mySettings.getToSearchFor();
  }

  @Override
  public IntroduceParameterData.ExpressionWrapper getParameterInitializer() {
    return myParameterInitializer;
  }

  @Nonnull
  @Override
  public String getParameterName() {
    return mySettings.getName();
  }

  @Override
  public int getReplaceFieldsWithGetters() {
    return mySettings.replaceFieldsWithGetters();
  }

  @Override
  public boolean isDeclareFinal() {
    return mySettings.declareFinal();
  }

  @Override
  public boolean isGenerateDelegate() {
    return mySettings.generateDelegate();
  }

  @Nonnull
  @Override
  public PsiType getForcedType() {
    final PsiType selectedType = mySettings.getSelectedType();
    if (selectedType != null) {
      return selectedType;
    }
    final PsiManager manager = PsiManager.getInstance(myProject);
    final GlobalSearchScope resolveScope = mySettings.getToReplaceIn().getResolveScope();
    return PsiType.getJavaLangObject(manager, resolveScope);
  }

  @Nonnull
  @Override
  public IntList getParametersToRemove() {
    return mySettings.parametersToRemove();
  }
}
