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

package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.java2groovy;

import com.intellij.java.impl.refactoring.introduceParameter.ExpressionConverter;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterData;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterMethodUsagesProcessor;
import com.intellij.java.impl.refactoring.introduceParameter.IntroduceParameterUtil;
import com.intellij.java.impl.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.javadoc.PsiDocTag;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.primitive.ints.IntList;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.GroovyIntroduceParameterUtil;

import java.util.function.IntConsumer;

/**
 * @author Maxim.Medvedev
 * Date: Apr 18, 2009 3:16:24 PM
 */
@ExtensionImpl
public class GroovyIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Logger LOG = Logger.getInstance(GroovyIntroduceParameterMethodUsagesProcessor.class);

  private static boolean isGroovyUsage(UsageInfo usage) {
    final PsiElement el = usage.getElement();
    return el != null && GroovyFileType.GROOVY_LANGUAGE.equals(el.getLanguage());
  }

  public boolean isMethodUsage(UsageInfo usage) {
    return GroovyRefactoringUtil.isMethodUsage(usage.getElement()) && isGroovyUsage(usage);
  }

  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, MultiMap<PsiElement, String> conflicts) {
  }

  public boolean processChangeMethodUsage(IntroduceParameterData data,
                                          UsageInfo usage,
                                          UsageInfo[] usages) throws IncorrectOperationException {
    GrCall callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(usage.getElement());
    if (callExpression == null) {
      return true;
    }
    GrArgumentList argList = callExpression.getArgumentList();
    GrExpression[] oldArgs = argList.getExpressionArguments();

    final GrExpression anchor;
    if (!data.getMethodToSearchFor().isVarArgs()) {
      anchor = getLast(oldArgs);
    }
    else {
      final PsiParameter[] parameters = data.getMethodToSearchFor().getParameterList().getParameters();
      if (parameters.length > oldArgs.length) {
        anchor = getLast(oldArgs);
      }
      else {
        final int lastNonVararg = parameters.length - 2;
        anchor = lastNonVararg >= 0 ? oldArgs[lastNonVararg] : null;
      }
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(argList, PsiMethod.class);

    GrClosureSignature signature = GrClosureSignatureUtil.createSignature(callExpression);
    if (signature == null) {
      signature = GrClosureSignatureUtil.createSignature(data.getMethodToSearchFor(), PsiSubstitutor.EMPTY);
    }

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs = GrClosureSignatureUtil
      .mapParametersToArguments(signature, callExpression.getNamedArguments(), callExpression.getExpressionArguments(),
                                callExpression.getClosureArguments(), callExpression, false, true);

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());

    if (method != null && IntroduceParameterUtil.isMethodInUsages(data, method, usages)) {
      argList.addAfter(factory.createExpressionFromText(data.getParameterName()), anchor);
    }
    else {
      final PsiElement _expr = data.getParameterInitializer().getExpression();
      PsiElement initializer = ExpressionConverter.getExpression(_expr, GroovyFileType.GROOVY_LANGUAGE, data.getProject());
      LOG.assertTrue(initializer instanceof GrExpression);

      GrExpression newArg = GroovyIntroduceParameterUtil.addClosureToCall(initializer, argList);
      if (newArg == null) {
        final PsiElement dummy = argList.addAfter(factory.createExpressionFromText("1"), anchor);
        newArg = ((GrExpression)dummy).replaceWithExpression((GrExpression)initializer, true);
      }
      final PsiMethod methodToReplaceIn = data.getMethodToReplaceIn();
      new OldReferencesResolver(callExpression, newArg, methodToReplaceIn, data.getReplaceFieldsWithGetters(), initializer,
                                signature, actualArgs, methodToReplaceIn.getParameterList().getParameters()).resolve();
      ChangeContextUtil.clearContextInfo(initializer);

      //newArg can be replaced by OldReferenceResolver
      if (newArg.isValid()) {
        JavaCodeStyleManager.getInstance(data.getProject()).shortenClassReferences(newArg);
        CodeStyleManager.getInstance(data.getProject()).reformat(newArg);
      }
    }

    if (actualArgs == null) {
      removeParamsFromUnresolvedCall(callExpression, data);
    }
    else {
      removeParametersFromCall(actualArgs, data.getParametersToRemove());
    }

    if (argList.getAllArguments().length == 0 && PsiImplUtil.hasClosureArguments(callExpression)) {
      final GrArgumentList emptyArgList = ((GrMethodCallExpression)factory.createExpressionFromText("foo{}")).getArgumentList();
      LOG.assertTrue(emptyArgList != null);
      argList.replace(emptyArgList);
    }
    return false;
  }

  @Nullable
  private static GrExpression getLast(GrExpression[] oldArgs) {
    GrExpression anchor;
    if (oldArgs.length > 0) {
      anchor = oldArgs[oldArgs.length - 1];
    }
    else {
      anchor = null;
    }
    return anchor;
  }

  private static void removeParametersFromCall(final GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs,
                                               final IntList parametersToRemove) {
    parametersToRemove.forEach(new IntConsumer() {
      public void accept(final int paramNum) {
        try {
          final GrClosureSignatureUtil.ArgInfo<PsiElement> actualArg = actualArgs[paramNum];
          if (actualArg == null) {
            return;
          }
          for (PsiElement arg : actualArg.args) {
            arg.delete();
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  private static void removeParamsFromUnresolvedCall(GrCall callExpression, IntroduceParameterData data) {
    final GrExpression[] arguments = callExpression.getExpressionArguments();
    final GrClosableBlock[] closureArguments = callExpression.getClosureArguments();
    final GrNamedArgument[] namedArguments = callExpression.getNamedArguments();

    final boolean hasNamedArgs;
    if (namedArguments.length > 0) {
      final PsiMethod method = data.getMethodToSearchFor();
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length > 0) {
        final PsiType type = parameters[0].getType();
        hasNamedArgs = InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
      }
      else {
        hasNamedArgs = false;
      }
    }
    else {
      hasNamedArgs = false;
    }

    IntList parametersToRemove = data.getParametersToRemove();
    for (int i = parametersToRemove.size() - 1; i >= 0; i--) {
      int paramNum = parametersToRemove.get(i);
      try {
        if (paramNum == 0 && hasNamedArgs) {
          for (GrNamedArgument namedArgument : namedArguments) {
            namedArgument.delete();
          }
        }
        else {
          if (hasNamedArgs) {
            paramNum--;
          }
          if (paramNum < arguments.length) {
            arguments[paramNum].delete();
          }
          else if (paramNum < arguments.length + closureArguments.length) {
            closureArguments[paramNum - arguments.length].delete();
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  public boolean processChangeMethodSignature(IntroduceParameterData data,
                                              UsageInfo usage,
                                              UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) {
      return true;
    }
    GrMethod method = (GrMethod)usage.getElement();

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(data.getParameterName(), method.getBlock());
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(method);
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    IntList parametersToRemove = data.getParametersToRemove();
    for (int i = parametersToRemove.size() - 1; i >= 0; i--) {
      int paramNum = parametersToRemove.get(i);
      try {
        PsiParameter param = parameters[paramNum];
        PsiDocTag tag = javaDocHelper.getTagForParameter(param);
        if (tag != null) {
          tag.delete();
        }
        param.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final PsiType forcedType = data.getForcedType();
    final String typeText = forcedType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) ? null : forcedType.getCanonicalText();

    GrParameter parameter = factory.createParameter(data.getParameterName(), typeText, method);
    parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, data.isDeclareFinal());
    final PsiParameter anchorParameter = getAnchorParameter(method);
    final GrParameterList parameterList = method.getParameterList();
    parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);
    JavaCodeStyleManager.getInstance(parameter.getProject()).shortenClassReferences(parameter);
    final PsiDocTag tagForAnchorParameter = javaDocHelper.getTagForParameter(anchorParameter);
    javaDocHelper.addParameterAfter(data.getParameterName(), tagForAnchorParameter);

    fieldConflictsResolver.fix();

    return false;

  }

  @Nullable
  private static PsiParameter getAnchorParameter(PsiMethod methodToReplaceIn) {
    PsiParameterList parameterList = methodToReplaceIn.getParameterList();
    final PsiParameter anchorParameter;
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (!methodToReplaceIn.isVarArgs()) {
      anchorParameter = length > 0 ? parameters[length - 1] : null;
    }
    else {
      anchorParameter = length > 1 ? parameters[length - 2] : null;
    }
    return anchorParameter;
  }

  public boolean processAddDefaultConstructor(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) {
    if (!(usage.getElement() instanceof PsiClass) || !isGroovyUsage(usage)) {
      return true;
    }
    PsiClass aClass = (PsiClass)usage.getElement();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());
    GrMethod constructor =
      factory.createConstructorFromText(aClass.getName(), ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, "{}");
    constructor = (GrMethod)aClass.add(constructor);
    constructor.getModifierList().setModifierProperty(VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
    processAddSuperCall(data, new UsageInfo(constructor), usages);
    return false;
  }

  public boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof GrMethod) || !isGroovyUsage(usage)) {
      return true;
    }
    GrMethod constructor = (GrMethod)usage.getElement();

    if (!constructor.isConstructor()) {
      return true;
    }

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(data.getProject());

    GrConstructorInvocation superCall = factory.createConstructorInvocation("super();");
    GrOpenBlock body = constructor.getBlock();
    final GrStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (GrConstructorInvocation)body.addStatementBefore(superCall, statements[0]);
    }
    else {
      superCall = (GrConstructorInvocation)body.addStatementBefore(superCall, null);
    }
    processChangeMethodUsage(data, new UsageInfo(superCall), usages);
    return false;

  }
}
