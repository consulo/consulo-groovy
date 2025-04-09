/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import com.intellij.java.impl.refactoring.changeSignature.*;
import com.intellij.java.impl.refactoring.util.CanonicalTypes;
import com.intellij.java.impl.refactoring.util.RefactoringUtil;
import com.intellij.java.impl.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.java.impl.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.java.language.impl.psi.scope.processor.VariablesProcessor;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.ide.impl.idea.refactoring.changeSignature.DefaultValueChooser;
import consulo.java.impl.refactoring.changeSignature.ChangeSignatureUsageProcessorEx;
import consulo.java.language.module.util.JavaClassNames;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.ResolveSnapshotProvider;
import consulo.language.editor.refactoring.changeSignature.ChangeInfo;
import consulo.language.editor.refactoring.changeSignature.ParameterInfo;
import consulo.language.impl.ast.Factory;
import consulo.language.impl.ast.SharedImplUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyNameSuggestionUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocParameterReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(order = "before javaProcessor", id = "groovyProcessor")
public class GrChangeSignatureUsageProcessor implements ChangeSignatureUsageProcessorEx {
    private static final Logger LOG = Logger.getInstance(GrChangeSignatureUsageProcessor.class);

    @Nonnull
    @Override
    public UsageInfo[] findUsages(@Nonnull ChangeInfo info) {
        if (info instanceof JavaChangeInfo) {
            return new GrChageSignatureUsageSearcher((JavaChangeInfo)info).findUsages();
        }
        return UsageInfo.EMPTY_ARRAY;
    }

    @Nonnull
    @Override
    public MultiMap<PsiElement, String> findConflicts(@Nonnull ChangeInfo info, SimpleReference<UsageInfo[]> refUsages) {
        if (info instanceof JavaChangeInfo javaChangeInfo) {
            return new GrChangeSignatureConflictSearcher(javaChangeInfo).findConflicts(refUsages);
        }
        else {
            return new MultiMap<>();
        }
    }

    @Override
    public boolean processPrimaryMethod(@Nonnull ChangeInfo changeInfo) {
        if (!(changeInfo instanceof GrChangeInfoImpl)) {
            return false;
        }

        GrChangeInfoImpl grInfo = (GrChangeInfoImpl)changeInfo;
        GrMethod method = grInfo.getMethod();
        if (grInfo.isGenerateDelegate()) {
            return generateDelegate(grInfo);
        }

        return processPrimaryMethodInner(grInfo, method, null);
    }

    @Override
    public boolean shouldPreviewUsages(ChangeInfo changeInfo, @Nonnull UsageInfo[] usages) {
        String newName = changeInfo.getNewName();
        if (newName != null && !StringUtil.isJavaIdentifier(newName)) {
            return true;
        }

        for (UsageInfo usage : usages) {
            if (usage instanceof GrMethodCallUsageInfo) {
                if (((GrMethodCallUsageInfo)usage).isPossibleUsage()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @RequiredUIAccess
    public boolean setupDefaultValues(ChangeInfo changeInfo, SimpleReference<UsageInfo[]> refUsages, Project project) {
        if (!(changeInfo instanceof JavaChangeInfo)) {
            return false;
        }
        for (UsageInfo usageInfo : refUsages.get()) {
            if (usageInfo instanceof GrMethodCallUsageInfo methodCallUsageInfo && methodCallUsageInfo.isToChangeArguments()) {
                PsiElement element = methodCallUsageInfo.getElement();
                if (element == null) {
                    continue;
                }
                PsiMethod caller = RefactoringUtil.getEnclosingMethod(element);
                boolean needDefaultValue = !((JavaChangeInfo)changeInfo).getMethodsToPropagateParameters().contains(caller);
                PsiMethod referencedMethod = methodCallUsageInfo.getReferencedMethod();
                if (needDefaultValue && (caller == null || referencedMethod == null
                    || !MethodSignatureUtil.isSuperMethod(referencedMethod, caller))) {
                    ParameterInfo[] parameters = changeInfo.getNewParameters();
                    for (ParameterInfo parameter : parameters) {
                        String defaultValue = parameter.getDefaultValue();
                        if (defaultValue == null && parameter.getOldIndex() == -1) {
                            ParameterInfoImpl parameterInfoImpl = (ParameterInfoImpl)parameter;
                            parameterInfoImpl.setDefaultValue("");
                            if (!Application.get().isUnitTestMode()) {
                                PsiType type = parameterInfoImpl.getTypeWrapper().getType(element, element.getManager());
                                DefaultValueChooser chooser =
                                    new DefaultValueChooser(project, parameter.getName(), PsiTypesUtil.getDefaultValueOfType(type));
                                chooser.show();
                                if (chooser.isOK()) {
                                    if (chooser.feelLucky()) {
                                        parameter.setUseAnySingleVariable(true);
                                    }
                                    else {
                                        parameterInfoImpl.setDefaultValue(chooser.getDefaultValue());
                                    }
                                }
                                else {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void registerConflictResolvers(
        @Nonnull List<ResolveSnapshotProvider.ResolveSnapshot> snapshots,
        @Nonnull ResolveSnapshotProvider resolveSnapshotProvider,
        @Nonnull UsageInfo[] usages,
        @Nonnull ChangeInfo changeInfo
    ) {
    }

    private static boolean generateDelegate(GrChangeInfoImpl grInfo) {
        GrMethod method = grInfo.getMethod();
        PsiClass psiClass = method.getContainingClass();
        GrMethod newMethod = (GrMethod)method.copy();
        newMethod = (GrMethod)psiClass.addAfter(newMethod, method);
        StringBuilder buffer = new StringBuilder();
        buffer.append("\n");
        if (method.isConstructor()) {
            buffer.append("this");
        }
        else {
            if (!PsiType.VOID.equals(method.getReturnType())) {
                buffer.append("return ");
            }
            buffer.append(GrChangeSignatureUtil.getNameWithQuotesIfNeeded(grInfo.getNewName(), method.getProject()));
        }

        generateParametersForDelegateCall(grInfo, method, buffer);

        GrCodeBlock codeBlock = GroovyPsiElementFactory.getInstance(method.getProject()).createMethodBodyFromText(buffer.toString());
        newMethod.setBlock(codeBlock);
        newMethod.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);

        CodeStyleManager.getInstance(method.getProject()).reformat(newMethod);
        return processPrimaryMethodInner(grInfo, method, null);
    }

    private static void generateParametersForDelegateCall(GrChangeInfoImpl grInfo, GrMethod method, StringBuilder buffer) {
        buffer.append("(");

        GrParameter[] oldParameters = method.getParameterList().getParameters();
        JavaParameterInfo[] parameters = grInfo.getNewParameters();

        String[] params = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            JavaParameterInfo parameter = parameters[i];
            int oldIndex = parameter.getOldIndex();
            if (oldIndex >= 0) {
                params[i] = oldParameters[oldIndex].getName();
            }
            else {
                params[i] = parameter.getDefaultValue();
            }
        }
        buffer.append(StringUtil.join(params, ","));
        buffer.append(");");
    }

    @RequiredWriteAction
    private static boolean processPrimaryMethodInner(JavaChangeInfo changeInfo, GrMethod method, @Nullable PsiMethod baseMethod) {
        if (changeInfo.isNameChanged()) {
            String newName = baseMethod == null
                ? changeInfo.getNewName()
                : RefactoringUtil.suggestNewOverriderName(method.getName(), baseMethod.getName(), changeInfo.getNewName());
            if (newName != null && !newName.equals(method.getName())) {
                method.setName(changeInfo.getNewName());
            }
        }

        GrModifierList modifierList = method.getModifierList();
        if (changeInfo.isVisibilityChanged()) {
            modifierList.setModifierProperty(changeInfo.getNewVisibility(), true);
        }

        PsiSubstitutor substitutor = baseMethod != null ? calculateSubstitutor(method, baseMethod) : PsiSubstitutor.EMPTY;

        PsiMethod context = changeInfo.getMethod();
        GrTypeElement oldReturnTypeElement = method.getReturnTypeElementGroovy();
        if (changeInfo.isReturnTypeChanged()) {
            CanonicalTypes.Type newReturnType = changeInfo.getNewReturnType();
            if (newReturnType == null) {
                if (oldReturnTypeElement != null) {
                    oldReturnTypeElement.delete();
                    if (modifierList.getModifiers().length == 0) {
                        modifierList.setModifierProperty(GrModifier.DEF, true);
                    }
                }
            }
            else {
                PsiType type = newReturnType.getType(context, method.getManager());
                method.setReturnType(substitutor.substitute(type));
                if (oldReturnTypeElement == null) {
                    modifierList.setModifierProperty(GrModifier.DEF, false);
                }
            }
        }

        JavaParameterInfo[] newParameters = changeInfo.getNewParameters();
        GrParameterList parameterList = method.getParameterList();
        GrParameter[] oldParameters = parameterList.getParameters();
        PsiParameter[] oldBaseParams = baseMethod != null ? baseMethod.getParameterList().getParameters() : null;

        Set<GrParameter> toRemove = new HashSet<>(oldParameters.length);
        ContainerUtil.addAll(toRemove, oldParameters);

        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
        GrParameter anchor = null;
        GrDocComment docComment = method.getDocComment();
        GrDocTag[] tags = docComment == null ? null : docComment.getTags();

        for (JavaParameterInfo newParameter : newParameters) {
            PsiType type;
            if (newParameter instanceof GrParameterInfo && ((GrParameterInfo)newParameter).hasNoType()) {
                type = null;
            }
            else {
                type = substitutor.substitute(newParameter.createType(context, method.getManager()));
            }

            //if old parameter name differs from base method parameter name we don't change it
            String newName;
            int oldIndex = newParameter.getOldIndex();
            if (oldIndex >= 0 && oldBaseParams != null) {
                String oldName = oldParameters[oldIndex].getName();
                if (oldName.equals(oldBaseParams[oldIndex].getName())) {
                    newName = newParameter.getName();
                }
                else {
                    newName = oldName;
                }
            }
            else {
                newName = newParameter.getName();
            }

            if (docComment != null) {
                if (oldIndex >= 0) {
                    GrParameter oldParameter = oldParameters[oldIndex];
                    String oldName = oldParameter.getName();
                    for (GrDocTag tag : tags) {
                        if ("@param".equals(tag.getName())) {
                            GrDocParameterReference parameterReference = tag.getDocParameterReference();
                            if (parameterReference != null && oldName.equals(parameterReference.getText())) {
                                parameterReference.handleElementRename(newName);
                            }
                        }
                    }
                }
            }

            GrParameter grParameter = factory.createParameter(
                newName,
                type == null ? null : type.getCanonicalText(),
                getInitializer(newParameter),
                parameterList
            );

            anchor = (GrParameter)parameterList.addAfter(grParameter, anchor);
        }

        for (GrParameter oldParameter : toRemove) {
            oldParameter.delete();
        }
        JavaCodeStyleManager.getInstance(parameterList.getProject()).shortenClassReferences(parameterList);
        CodeStyleManager.getInstance(parameterList.getProject()).reformat(parameterList);

        if (changeInfo.isExceptionSetOrOrderChanged()) {
            ThrownExceptionInfo[] infos = changeInfo.getNewExceptions();
            PsiClassType[] exceptionTypes = new PsiClassType[infos.length];
            for (int i = 0; i < infos.length; i++) {
                ThrownExceptionInfo info = infos[i];
                exceptionTypes[i] = (PsiClassType)info.createType(method, method.getManager());
            }

            PsiReferenceList thrownList = GroovyPsiElementFactory.getInstance(method.getProject()).createThrownList(exceptionTypes);
            thrownList = (PsiReferenceList)method.getThrowsList().replace(thrownList);
            JavaCodeStyleManager.getInstance(thrownList.getProject()).shortenClassReferences(thrownList);
            CodeStyleManager.getInstance(method.getProject()).reformat(method.getThrowsList());
        }
        return true;
    }

    private static PsiSubstitutor calculateSubstitutor(PsiMethod derivedMethod, PsiMethod baseMethod) {
        PsiSubstitutor substitutor;
        if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
            substitutor = PsiSubstitutor.EMPTY;
        }
        else {
            PsiClass baseClass = baseMethod.getContainingClass();
            PsiClass derivedClass = derivedMethod.getContainingClass();
            if (baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
                PsiSubstitutor superClassSubstitutor =
                    TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, PsiSubstitutor.EMPTY);
                MethodSignature superMethodSignature = baseMethod.getSignature(superClassSubstitutor);
                MethodSignature methodSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
                PsiSubstitutor superMethodSubstitutor =
                    MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
                substitutor = superMethodSubstitutor != null ? superMethodSubstitutor : superClassSubstitutor;
            }
            else {
                substitutor = PsiSubstitutor.EMPTY;
            }
        }
        return substitutor;
    }

    @Nullable
    @RequiredReadAction
    private static <Type extends PsiElement, List extends PsiElement> Type getNextOfType(
        List parameterList,
        PsiElement current,
        Class<Type> type
    ) {
        return current != null ? PsiTreeUtil.getNextSiblingOfType(current, type) : PsiTreeUtil.getChildOfType(parameterList, type);
    }

    @Nullable
    private static String getInitializer(JavaParameterInfo newParameter) {
        if (newParameter instanceof GrParameterInfo) {
            return ((GrParameterInfo)newParameter).getDefaultInitializer();
        }
        return null;
    }

    @Override
    @RequiredWriteAction
    public boolean processUsage(
        @Nonnull ChangeInfo changeInfo,
        @Nonnull UsageInfo usageInfo,
        boolean beforeMethodChange,
        @Nonnull UsageInfo[] usages
    ) {
        if (!(changeInfo instanceof JavaChangeInfo javaChangeInfo)) {
            return false;
        }

        PsiElement element = usageInfo.getElement();
        if (element == null) {
            return false;
        }
        if (!GroovyLanguage.INSTANCE.equals(element.getLanguage())) {
            return false;
        }

        if (beforeMethodChange) {
            if (usageInfo instanceof OverriderUsageInfo overriderUsageInfo) {
                processPrimaryMethodInner(
                    javaChangeInfo,
                    (GrMethod)overriderUsageInfo.getElement(),
                    overriderUsageInfo.getBaseMethod()
                );
            }
        }
        else {
            if (usageInfo instanceof GrMethodCallUsageInfo methodCallUsageInfo) {
                processMethodUsage(
                    element,
                    javaChangeInfo,
                    methodCallUsageInfo.isToChangeArguments(),
                    methodCallUsageInfo.isToCatchExceptions(),
                    methodCallUsageInfo.getMapToArguments(),
                    methodCallUsageInfo.getSubstitutor()
                );
                return true;
            }
            else if (usageInfo instanceof DefaultConstructorImplicitUsageInfo defaultConstructorImplicitUsageInfo) {
                processConstructor((GrMethod)defaultConstructorImplicitUsageInfo.getConstructor(), javaChangeInfo);
                return true;
            }
            else if (usageInfo instanceof NoConstructorClassUsageInfo noConstructorClassUsageInfo) {
                processClassUsage((GrTypeDefinition)noConstructorClassUsageInfo.getPsiClass(), javaChangeInfo);
                return true;
            }
            else if (usageInfo instanceof ChangeSignatureParameterUsageInfo changeSignatureParameterUsageInfo) {
                String newName = changeSignatureParameterUsageInfo.newParameterName;
                ((PsiReference)element).handleElementRename(newName);
                return true;
            }
            else {
                PsiReference ref = element.getReference();
                if (ref != null && changeInfo.getMethod() != null) {
                    ref.bindToElement(changeInfo.getMethod());
                    return true;
                }
            }
        }
        return false;
    }

    private static void processClassUsage(GrTypeDefinition psiClass, JavaChangeInfo changeInfo) {
        String name = psiClass.getName();

        GrMethod constructor = GroovyPsiElementFactory.getInstance(psiClass.getProject()).createConstructorFromText(
            name,
            ArrayUtil.EMPTY_STRING_ARRAY,
            ArrayUtil.EMPTY_STRING_ARRAY,
            "{}",
            null
        );

        GrModifierList list = constructor.getModifierList();
        if (psiClass.isPrivate()) {
            list.setModifierProperty(PsiModifier.PRIVATE, true);
        }
        if (psiClass.isProtected()) {
            list.setModifierProperty(PsiModifier.PROTECTED, true);
        }
        if (!list.hasExplicitVisibilityModifiers()) {
            list.setModifierProperty(GrModifier.DEF, true);
        }

        constructor = (GrMethod)psiClass.add(constructor);
        processConstructor(constructor, changeInfo);
    }

    private static void processConstructor(GrMethod constructor, JavaChangeInfo changeInfo) {
        PsiClass containingClass = constructor.getContainingClass();
        PsiClass baseClass = changeInfo.getMethod().getContainingClass();
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, containingClass, PsiSubstitutor.EMPTY);

        GrOpenBlock block = constructor.getBlock();
        GrConstructorInvocation invocation =
            GroovyPsiElementFactory.getInstance(constructor.getProject()).createConstructorInvocation("super()");
        invocation = (GrConstructorInvocation)block.addStatementBefore(invocation, getFirstStatement(block));
        processMethodUsage(
            invocation.getInvokedExpression(),
            changeInfo,
            changeInfo.isParameterSetOrOrderChanged() || changeInfo.isParameterNamesChanged(),
            changeInfo.isExceptionSetChanged(),
            GrClosureSignatureUtil.ArgInfo.<PsiElement>empty_array(),
            substitutor
        );
    }

    @Nullable
    private static GrStatement getFirstStatement(GrCodeBlock block) {
        GrStatement[] statements = block.getStatements();
        if (statements.length == 0) {
            return null;
        }
        return statements[0];
    }

    @RequiredWriteAction
    private static void processMethodUsage(
        PsiElement element,
        JavaChangeInfo changeInfo,
        boolean toChangeArguments,
        boolean toCatchExceptions,
        GrClosureSignatureUtil.ArgInfo<PsiElement>[] map,
        PsiSubstitutor substitutor
    ) {
        if (map == null) {
            return;
        }
        if (changeInfo.isNameChanged() && element instanceof GrReferenceElement refElem) {
            element = refElem.handleElementRename(changeInfo.getNewName());
        }
        if (toChangeArguments) {
            JavaParameterInfo[] parameters = changeInfo.getNewParameters();
            GrArgumentList argumentList = PsiUtil.getArgumentsList(element);
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
            if (argumentList == null) {
                if (element instanceof GrEnumConstant) {
                    argumentList = factory.createArgumentList();
                    argumentList = (GrArgumentList)element.add(argumentList);
                }
                else {
                    return;
                }
            }
            Set<PsiElement> argsToDelete = new HashSet<>(map.length * 2);
            for (GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo : map) {
                argsToDelete.addAll(argInfo.args);
            }

            for (JavaParameterInfo parameter : parameters) {
                int index = parameter.getOldIndex();
                if (index >= 0) {
                    argsToDelete.removeAll(map[index].args);
                }
            }

            for (PsiElement arg : argsToDelete) {
                arg.delete();
            }


            boolean skipOptionals = false;
            PsiElement anchor = null; //PsiTreeUtil.getChildOfAnyType(argumentList, GrExpression.class, GrNamedArgument.class);
            for (int i = 0; i < parameters.length; i++) {
                JavaParameterInfo parameter = parameters[i];
                int index = parameter.getOldIndex();
                if (index >= 0) {
                    GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo = map[index];
                    List<PsiElement> arguments = argInfo.args;
                    if (argInfo.isMultiArg) { //arguments for Map and varArg
                        if ((i != 0 || !(arguments.size() > 0 && arguments.iterator().next() instanceof GrNamedArgument))
                            && (i != parameters.length - 1 || !parameter.isVarargType())) {
                            PsiType type = parameter.createType(changeInfo.getMethod().getParameterList(), argumentList.getManager());
                            GrExpression arg =
                                GroovyRefactoringUtil.generateArgFromMultiArg(substitutor, arguments, type, element.getProject());
                            for (PsiElement argument : arguments) {
                                argument.delete();
                            }
                            anchor = argumentList.addAfter(arg, anchor);
                            JavaCodeStyleManager.getInstance(anchor.getProject()).shortenClassReferences(anchor);
                        }
                    }
                    else if (arguments.size() == 1) { //arg exists
                        PsiElement arg = arguments.iterator().next();
                        if (i == parameters.length - 1 && parameter.isVarargType()
                            && arg instanceof GrSafeCastExpression safeCastExpression
                            && safeCastExpression.getOperand() instanceof GrListOrMap listOrMap && !listOrMap.isMap()) {

                            GrListOrMap copy = (GrListOrMap)listOrMap.copy();
                            PsiElement[] newVarargs = copy.getInitializers();
                            for (PsiElement vararg : newVarargs) {
                                anchor = argumentList.addAfter(vararg, anchor);
                            }
                            arg.delete();
                            continue;
                        }

                        PsiElement curArg = getNextOfType(argumentList, anchor, GrExpression.class);
                        if (curArg == arg) {
                            anchor = arg;
                        }
                        else {
                            PsiElement copy = arg.copy();
                            anchor = argumentList.addAfter(copy, anchor);
                            arg.delete();
                        }
                    }
                    else { //arg is skipped. Parameter is optional
                        skipOptionals = true;
                    }
                }
                else {
                    if (skipOptionals && isParameterOptional(parameter)) {
                        continue;
                    }

                    if (forceOptional(parameter)) {
                        skipOptionals = true;
                        continue;
                    }
                    try {

                        GrExpression value = createDefaultValue(factory, changeInfo, parameter, argumentList);
                        if (i > 0 && (value == null || anchor == null)) {
                            PsiElement comma = Factory.createSingleLeafElement(
                                GroovyTokenTypes.mCOMMA,
                                ",",
                                0,
                                1,
                                SharedImplUtil.findCharTableByTree(argumentList.getNode()),
                                argumentList.getManager()
                            ).getPsi();
                            if (anchor == null) {
                                anchor = argumentList.getLeftParen();
                            }

                            anchor = argumentList.addAfter(comma, anchor);
                        }
                        if (value != null) {
                            anchor = argumentList.addAfter(value, anchor);
                        }
                    }
                    catch (IncorrectOperationException e) {
                        LOG.error(e.getMessage());
                    }
                }
            }

            GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
            if (argumentList.getText().trim().length() == 0 && (call == null || !PsiImplUtil.hasClosureArguments(call))) {
                argumentList = argumentList.replaceWithArgumentList(factory.createArgumentList());
            }
            CodeStyleManager.getInstance(argumentList.getProject()).reformat(argumentList);
        }

        if (toCatchExceptions) {
            ThrownExceptionInfo[] exceptionInfos = changeInfo.getNewExceptions();
            PsiClassType[] exceptions = getExceptions(exceptionInfos, element, element.getManager());
            fixExceptions(element, exceptions);
        }
    }

    @Nullable
    @RequiredReadAction
    private static GrExpression createDefaultValue(
        GroovyPsiElementFactory factory,
        JavaChangeInfo changeInfo,
        JavaParameterInfo info,
        GrArgumentList list
    ) {
        if (info.isUseAnySingleVariable()) {
            PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(list.getProject()).getResolveHelper();
            PsiType type = info.getTypeWrapper().getType(changeInfo.getMethod(), list.getManager());
            VariablesProcessor processor = new VariablesProcessor(false) {
                @RequiredReadAction
                @Override
                protected boolean check(PsiVariable var, ResolveState state) {
                    if (var instanceof PsiField && !resolveHelper.isAccessible((PsiField)var, list, null)) {
                        return false;
                    }
                    if (var instanceof GrVariable &&
                        GroovyRefactoringUtil.isLocalVariable(var) &&
                        list.getTextRange().getStartOffset() <= var.getTextRange().getStartOffset()) {
                        return false;
                    }
                    if (PsiTreeUtil.isAncestor(var, list, false)) {
                        return false;
                    }
                    PsiType _type = var instanceof GrVariable variable ? variable.getTypeGroovy() : var.getType();
                    PsiType varType = state.get(PsiSubstitutor.KEY).substitute(_type);
                    return type.isAssignableFrom(varType);
                }

                @Override
                public boolean execute(@Nonnull PsiElement pe, ResolveState state) {
                    super.execute(pe, state);
                    return size() < 2;
                }
            };
            ResolveUtil.treeWalkUp(list, processor, false);
            if (processor.size() == 1) {
                PsiVariable result = processor.getResult(0);
                return factory.createExpressionFromText(result.getName(), list);
            }
            if (processor.size() == 0) {
                PsiClass parentClass = PsiTreeUtil.getParentOfType(list, PsiClass.class);
                if (parentClass != null) {
                    PsiClass containingClass = parentClass;
                    Set<PsiClass> containingClasses = new HashSet<>();
                    PsiElementFactory jfactory = JavaPsiFacade.getElementFactory(list.getProject());
                    while (containingClass != null) {
                        if (type.isAssignableFrom(jfactory.createType(containingClass, PsiSubstitutor.EMPTY))) {
                            containingClasses.add(containingClass);
                        }
                        containingClass = PsiTreeUtil.getParentOfType(containingClass, PsiClass.class);
                    }
                    if (containingClasses.size() == 1) {
                        return factory.createThisExpression(
                            containingClasses.contains(parentClass) ? null : containingClasses.iterator().next()
                        );
                    }
                }
            }
        }

        String value = info.getDefaultValue();
        return !StringUtil.isEmpty(value) ? factory.createExpressionFromText(value, list) : null;
    }

    protected static boolean forceOptional(JavaParameterInfo parameter) {
        return parameter instanceof GrParameterInfo && ((GrParameterInfo)parameter).forceOptional();
    }

    private static void fixExceptions(PsiElement element, PsiClassType[] exceptions) {
        if (exceptions.length == 0) {
            return;
        }
        GroovyPsiElement context = PsiTreeUtil.getParentOfType(
            element,
            GrTryCatchStatement.class,
            GrClosableBlock.class,
            GrMethod.class,
            GroovyFile.class
        );
        if (context instanceof GrClosableBlock) {
            element = generateTryCatch(element, exceptions);
        }
        else if (context instanceof GrMethod method) {
            PsiClassType[] handledExceptions = method.getThrowsList().getReferencedTypes();
            List<PsiClassType> psiClassTypes = filterOutExceptions(exceptions, context, handledExceptions);
            element = generateTryCatch(element, psiClassTypes.toArray(new PsiClassType[psiClassTypes.size()]));
        }
        else if (context instanceof GroovyFile) {
            element = generateTryCatch(element, exceptions);
        }
        else if (context instanceof GrTryCatchStatement tryCatchStmt) {
            GrCatchClause[] catchClauses = tryCatchStmt.getCatchClauses();
            List<PsiClassType> referencedTypes = ContainerUtil.map(catchClauses, new Function<GrCatchClause, PsiClassType>() {
                @Override
                @Nullable
                public PsiClassType apply(GrCatchClause grCatchClause) {
                    GrParameter grParameter = grCatchClause.getParameter();
                    PsiType type = grParameter != null ? grParameter.getType() : null;
                    return type instanceof PsiClassType classType ? classType : null;
                }
            });

            referencedTypes = ContainerUtil.skipNulls(referencedTypes);
            List<PsiClassType> psiClassTypes = filterOutExceptions(
                exceptions,
                context,
                referencedTypes.toArray(new PsiClassType[referencedTypes.size()])
            );

            element = fixCatchBlock((GrTryCatchStatement)context, psiClassTypes.toArray(new PsiClassType[psiClassTypes.size()]));
        }

        //  CodeStyleManager.getInstance(element.getProject()).reformat(element);
    }

    private static PsiElement generateTryCatch(PsiElement element, PsiClassType[] exceptions) {
        if (exceptions.length == 0) {
            return element;
        }
        GrTryCatchStatement tryCatch = (GrTryCatchStatement)GroovyPsiElementFactory.getInstance(element.getProject())
            .createStatementFromText("try{} catch (Exception e){}");
        GrStatement statement = PsiTreeUtil.getParentOfType(element, GrStatement.class);
        assert statement != null;
        tryCatch.getTryBlock().addStatementBefore(statement, null);
        tryCatch = (GrTryCatchStatement)statement.replace(tryCatch);
        tryCatch.getCatchClauses()[0].delete();
        fixCatchBlock(tryCatch, exceptions);
        return tryCatch;
    }

    private static PsiElement fixCatchBlock(GrTryCatchStatement tryCatch, PsiClassType[] exceptions) {
        if (exceptions.length == 0) {
            return tryCatch;
        }
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(tryCatch.getProject());

        GrCatchClause[] clauses = tryCatch.getCatchClauses();
        List<String> restricted = ContainerUtil.map(clauses, new Function<GrCatchClause, String>() {
            @Override
            @Nullable
            public String apply(GrCatchClause grCatchClause) {
                GrParameter grParameter = grCatchClause.getParameter();
                return grParameter != null ? grParameter.getName() : null;
            }
        });

        restricted = ContainerUtil.skipNulls(restricted);
        DefaultGroovyVariableNameValidator nameValidator = new DefaultGroovyVariableNameValidator(tryCatch, restricted);

        GrCatchClause anchor = clauses.length == 0 ? null : clauses[clauses.length - 1];
        for (PsiClassType type : exceptions) {
            String[] names = GroovyNameSuggestionUtil.suggestVariableNameByType(type, nameValidator);
            GrCatchClause catchClause = factory.createCatchClause(type, names[0]);
            GrStatement printStackTrace = factory.createStatementFromText(names[0] + ".printStackTrace()");
            catchClause.getBody().addStatementBefore(printStackTrace, null);
            anchor = tryCatch.addCatchClause(catchClause, anchor);
            JavaCodeStyleManager.getInstance(tryCatch.getProject()).shortenClassReferences(anchor);
        }
        return tryCatch;
    }

    private static List<PsiClassType> filterOutExceptions(
        PsiClassType[] exceptions,
        GroovyPsiElement context,
        PsiClassType[] handledExceptions
    ) {
        return ContainerUtil.findAll(
            exceptions,
            o -> {
                if (!InheritanceUtil.isInheritor(o, JavaClassNames.JAVA_LANG_EXCEPTION)) {
                    return false;
                }
                for (PsiClassType type : handledExceptions) {
                    if (TypesUtil.isAssignableByMethodCallConversion(type, o, context)) {
                        return false;
                    }
                }
                return true;
            }
        );
    }

    private static PsiClassType[] getExceptions(ThrownExceptionInfo[] infos, PsiElement context, PsiManager manager) {
        return ContainerUtil.map(
            infos,
            new Function<>() {
                @Override
                @Nullable
                public PsiClassType apply(ThrownExceptionInfo thrownExceptionInfo) {
                    return (PsiClassType)thrownExceptionInfo.createType(context, manager);
                }
            },
            new PsiClassType[infos.length]
        );
    }

    private static boolean isParameterOptional(JavaParameterInfo parameterInfo) {
        return parameterInfo instanceof GrParameterInfo && ((GrParameterInfo)parameterInfo).isOptional();
    }
}
