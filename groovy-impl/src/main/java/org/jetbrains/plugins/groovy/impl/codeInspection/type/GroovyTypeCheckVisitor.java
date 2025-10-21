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
package org.jetbrains.plugins.groovy.impl.codeInspection.type;

import com.intellij.java.language.impl.psi.impl.PsiSubstitutorImpl;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyNamedArgumentProvider;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.impl.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.assignment.*;
import org.jetbrains.plugins.groovy.impl.lang.psi.typeEnhancers.ClosureParamsEnhancer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrBuilderMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ConversionResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrTypeConverter.ApplicableTo;
import org.jetbrains.plugins.groovy.lang.psi.util.*;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.intellij.java.language.psi.util.PsiUtil.extractIterableTypeParameter;
import static org.jetbrains.plugins.groovy.impl.codeInspection.type.GroovyTypeCheckVisitorHelper.*;

public class GroovyTypeCheckVisitor extends BaseInspectionVisitor {
    private static final Logger LOG = Logger.getInstance(GroovyAssignabilityCheckInspection.class);

    @RequiredReadAction
    private boolean checkCallApplicability(@Nullable PsiType type, boolean checkUnknownArgs, @Nonnull CallInfo info) {
        PsiType[] argumentTypes = info.getArgumentTypes();
        GrExpression invoked = info.getInvokedExpression();
        if (invoked == null) {
            return true;
        }

        if (type instanceof GrClosureType closureType) {
            if (argumentTypes == null) {
                return true;
            }

            GrClosureSignatureUtil.ApplicabilityResult result =
                PsiUtil.isApplicableConcrete(argumentTypes, closureType, info.getCall());
            return switch (result) {
                case inapplicable -> {
                    registerCannotApplyError(invoked.getText(), info);
                    yield false;
                }
                case canBeApplicable -> {
                    if (checkUnknownArgs) {
                        highlightUnknownArgs(info);
                    }
                    yield !checkUnknownArgs;
                }
                default -> true;
            };
        }
        else if (type != null) {
            GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", invoked, argumentTypes);
            for (GroovyResolveResult result : calls) {
                PsiElement resolved = result.getElement();
                if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
                    if (!checkMethodApplicability(result, checkUnknownArgs, info)) {
                        return false;
                    }
                }
                else if (resolved instanceof PsiField field) {
                    if (!checkCallApplicability(field.getType(), checkUnknownArgs && calls.length == 1, info)) {
                        return false;
                    }
                }
            }
            if (calls.length == 0 && !(invoked instanceof GrString)) {
                registerCannotApplyError(invoked.getText(), info);
            }
            return true;
        }
        return true;
    }

    private boolean checkCannotInferArgumentTypes(@Nonnull CallInfo info) {
        if (info.getArgumentTypes() != null) {
            return true;
        }
        else {
            highlightUnknownArgs(info);
            return false;
        }
    }

    private <T extends GroovyPsiElement> boolean checkConstructorApplicability(
        @Nonnull GroovyResolveResult constructorResolveResult,
        @Nonnull CallInfo<T> info,
        boolean checkUnknownArgs
    ) {
        PsiElement element = constructorResolveResult.getElement();
        LOG.assertTrue(element instanceof PsiMethod method && method.isConstructor(), element);
        PsiMethod constructor = (PsiMethod) element;

        GrArgumentList argList = info.getArgumentList();
        if (argList != null) {
            GrExpression[] exprArgs = argList.getExpressionArguments();
            if (exprArgs.length == 0 && !PsiUtil.isConstructorHasRequiredParameters(constructor)) {
                return true;
            }
        }

        PsiType[] types = info.getArgumentTypes();
        PsiClass containingClass = constructor.getContainingClass();
        if (types != null && containingClass != null) {
            final PsiType[] newTypes = GrInnerClassConstructorUtil.addEnclosingArgIfNeeded(types, info.getCall(), containingClass);
            if (newTypes.length != types.length) {
                return checkMethodApplicability(
                    constructorResolveResult,
                    checkUnknownArgs,
                    new DelegatingCallInfo<>(info) {
                        @Nullable
                        @Override
                        public PsiType[] getArgumentTypes() {
                            return newTypes;
                        }
                    }
                );
            }
        }

        return checkMethodApplicability(constructorResolveResult, checkUnknownArgs, info);
    }

    @RequiredReadAction
    private void processConstructorCall(@Nonnull ConstructorCallInfo<?> info) {
        if (hasErrorElements(info.getArgumentList())) {
            return;
        }

        if (!checkCannotInferArgumentTypes(info)) {
            return;
        }
        GroovyResolveResult constructorResolveResult = info.advancedResolve();
        PsiElement constructor = constructorResolveResult.getElement();

        if (constructor != null) {
            if (!checkConstructorApplicability(constructorResolveResult, info, true)) {
                return;
            }
        }
        else {
            GroovyResolveResult[] results = info.multiResolve();
            if (results.length > 0) {
                for (GroovyResolveResult result : results) {
                    if (result.getElement() instanceof PsiMethod && !checkConstructorApplicability(result, info, false)) {
                        return;
                    }
                }
                registerError(
                    info.getElementToHighlight(),
                    GroovyLocalize.constructorCallIsAmbiguous(),
                    null,
                    ProblemHighlightType.GENERIC_ERROR
                );
            }
            else {
                GrExpression[] expressionArguments = info.getExpressionArguments();
                boolean hasClosureArgs = info.getClosureArguments().length > 0;
                boolean hasNamedArgs = info.getNamedArguments().length > 0;
                if (hasClosureArgs
                    || hasNamedArgs && expressionArguments.length > 0
                    || !hasNamedArgs && expressionArguments.length > 0 && !isOnlyOneMapParam(expressionArguments)) {
                    GroovyResolveResult[] resolveResults = info.multiResolveClass();
                    if (resolveResults.length == 1 && resolveResults[0].getElement() instanceof PsiClass psiClass) {
                        registerError(
                            info.getElementToHighlight(),
                            GroovyLocalize.cannotApplyDefaultConstructor(psiClass.getName()),
                            null,
                            ProblemHighlightType.GENERIC_ERROR
                        );
                        return;
                    }
                }
            }
        }

        checkNamedArgumentsType(info);
    }

    private boolean checkForImplicitEnumAssigning(
        @Nullable PsiType expectedType,
        @Nonnull GrExpression expression,
        @Nonnull GroovyPsiElement elementToHighlight
    ) {
        if (!(expectedType instanceof PsiClassType classType)) {
            return false;
        }

        if (!GroovyConfigUtils.getInstance().isVersionAtLeast(elementToHighlight, GroovyConfigUtils.GROOVY1_8)) {
            return false;
        }

        PsiClass resolved = classType.resolve();
        if (resolved == null || !resolved.isEnum()) {
            return false;
        }

        PsiType type = expression.getType();
        if (type == null) {
            return false;
        }

        if (!type.equalsToText(GroovyCommonClassNames.GROOVY_LANG_GSTRING)
            && !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            return false;
        }

        if (GroovyConstantExpressionEvaluator.evaluate(expression) instanceof String stringResult) {
            if (!(resolved.findFieldByName(stringResult, true) instanceof PsiEnumConstant)) {
                registerError(
                    elementToHighlight,
                    GroovyLocalize.cannotFindEnumConstant0InEnum1(stringResult, expectedType.getPresentableText()),
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            }
        }
        else {
            registerError(
                elementToHighlight,
                GroovyLocalize.cannotAssignStringToEnum0(expectedType.getPresentableText()),
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.WEAK_WARNING
            );
        }
        return true;
    }

    @RequiredReadAction
    private void checkIndexProperty(@Nonnull CallInfo<? extends GrIndexProperty> info) {
        if (hasErrorElements(info.getArgumentList())) {
            return;
        }

        if (!checkCannotInferArgumentTypes(info)) {
            return;
        }

        PsiType type = info.getQualifierInstanceType();
        PsiType[] types = info.getArgumentTypes();

        if (checkSimpleArrayAccess(info, type, types)) {
            return;
        }

        GroovyResolveResult[] results = info.multiResolve();
        GroovyResolveResult resolveResult = info.advancedResolve();

        if (resolveResult.getElement() != null) {
            PsiElement resolved = resolveResult.getElement();

            if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
                checkMethodApplicability(resolveResult, true, info);
            }
            else if (resolved instanceof GrField field) {
                checkCallApplicability(field.getTypeGroovy(), true, info);
            }
            else if (resolved instanceof PsiField field) {
                checkCallApplicability(field.getType(), true, info);
            }
        }
        else if (results.length > 0) {
            for (GroovyResolveResult result : results) {
                PsiElement resolved = result.getElement();
                if (resolved instanceof PsiMethod && !result.isInvokedOnProperty()) {
                    if (!checkMethodApplicability(result, false, info)) {
                        return;
                    }
                }
                else if (resolved instanceof GrField field) {
                    if (!checkCallApplicability(field.getTypeGroovy(), false, info)) {
                        return;
                    }
                }
                else if (resolved instanceof PsiField field) {
                    if (!checkCallApplicability(field.getType(), false, info)) {
                        return;
                    }
                }
            }

            registerError(
                info.getElementToHighlight(),
                GroovyLocalize.methodCallIsAmbiguous(),
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR
            );
        }
        else {
            String typesString = buildArgTypesList(types);
            registerError(
                info.getElementToHighlight(),
                GroovyLocalize.cannotFindOperatorOverloadMethod(typesString),
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR
            );
        }
    }

    private <T extends GroovyPsiElement> boolean checkMethodApplicability(
        @Nonnull final GroovyResolveResult methodResolveResult,
        boolean checkUnknownArgs,
        @Nonnull final CallInfo<T> info
    ) {
        if (!(methodResolveResult.getElement() instanceof PsiMethod method && !(method instanceof GrBuilderMethod))) {
            return true;
        }

        if ("call".equals(method.getName()) && info.getInvokedExpression() instanceof GrReferenceExpression invokedExpr) {
            GrExpression qualifierExpression = invokedExpr.getQualifierExpression();
            if (qualifierExpression != null && qualifierExpression.getType() instanceof GrClosureType closureType) {
                GrClosureSignatureUtil.ApplicabilityResult result =
                    PsiUtil.isApplicableConcrete(info.getArgumentTypes(), closureType, info.getInvokedExpression());
                return switch (result) {
                    case inapplicable -> {
                        highlightInapplicableMethodUsage(methodResolveResult, info, method);
                        yield false;
                    }
                    case canBeApplicable -> {
                        if (checkUnknownArgs) {
                            highlightUnknownArgs(info);
                        }
                        yield !checkUnknownArgs;
                    }
                    default -> true;
                };
            }
        }

        if (method instanceof GrGdkMethod gdkMethod && info.getInvokedExpression() instanceof GrReferenceExpression invoked) {
            GrExpression qualifier = PsiImplUtil.getRuntimeQualifier(invoked);
            if (qualifier == null && method.getName().equals("call")) {
                GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(invoked.getProject());
                final GrReferenceExpression callRef = factory.createReferenceExpressionFromText("qualifier.call", invoked);
                callRef.setQualifier(invoked);
                return checkMethodApplicability(methodResolveResult, checkUnknownArgs, new DelegatingCallInfo<T>(info) {
                    @Nullable
                    @Override
                    public GrExpression getInvokedExpression() {
                        return callRef;
                    }

                    @Nonnull
                    @Override
                    public GroovyResolveResult advancedResolve() {
                        return methodResolveResult;
                    }

                    @Nonnull
                    @Override
                    public GroovyResolveResult[] multiResolve() {
                        return new GroovyResolveResult[]{methodResolveResult};
                    }

                    @Nullable
                    @Override
                    public PsiType getQualifierInstanceType() {
                        return info.getInvokedExpression().getType();
                    }
                });
            }
            PsiMethod staticMethod = gdkMethod.getStaticMethod();
            PsiType qualifierType = info.getQualifierInstanceType();

            //check methods processed by @Category(ClassWhichProcessMethod) annotation
            if (qualifierType != null
                && !GdkMethodUtil.isCategoryMethod(staticMethod, qualifierType, qualifier, methodResolveResult.getSubstitutor())
                && !checkCategoryQualifier(invoked, qualifier, staticMethod, methodResolveResult.getSubstitutor())) {
                registerError(
                    info.getHighlightElementForCategoryQualifier(),
                    GroovyInspectionLocalize.categoryMethod0CannotBeAppliedTo1(
                        method.getName(),
                        qualifierType.getCanonicalText()
                    ),
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.GENERIC_ERROR
                );
                return false;
            }
        }

        if (info.getArgumentTypes() == null) {
            return true;
        }

        GrClosureSignatureUtil.ApplicabilityResult applicable =
            PsiUtil.isApplicableConcrete(info.getArgumentTypes(), method, methodResolveResult.getSubstitutor(), info.getCall(), false);
        return switch (applicable) {
            case inapplicable -> {
                highlightInapplicableMethodUsage(methodResolveResult, info, method);
                yield false;
            }
            case canBeApplicable -> {
                if (checkUnknownArgs) {
                    highlightUnknownArgs(info);
                }
                yield !checkUnknownArgs;
            }
            default -> true;
        };
    }

    @RequiredReadAction
    private void checkMethodCall(@Nonnull CallInfo<? extends GrMethodCall> info) {
        if (hasErrorElements(info.getArgumentList())) {
            return;
        }

        if (info.getInvokedExpression() instanceof GrReferenceExpression refExpr) {
            GroovyResolveResult resolveResult = info.advancedResolve();
            GroovyResolveResult[] results = info.multiResolve();

            PsiElement resolved = resolveResult.getElement();
            if (resolved == null) {
                GrExpression qualifier = refExpr.getQualifierExpression();
                if (qualifier == null && GrHighlightUtil.isDeclarationAssignment(refExpr)) {
                    return;
                }
            }

            if (!checkCannotInferArgumentTypes(info)) {
                return;
            }

            if (resolved != null) {
                if (resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty()) {
                    checkMethodApplicability(resolveResult, true, info);
                }
                else {
                    checkCallApplicability(refExpr.getType(), true, info);
                }
            }
            else if (results.length > 0) {
                for (GroovyResolveResult result : results) {
                    if (result.getElement() instanceof PsiMethod && !result.isInvokedOnProperty()) {
                        if (!checkMethodApplicability(result, false, info)) {
                            return;
                        }
                    }
                    else {
                        if (!checkCallApplicability(refExpr.getType(), false, info)) {
                            return;
                        }
                    }
                }

                registerError(info.getElementToHighlight(), GroovyLocalize.methodCallIsAmbiguous());
            }
        }
        else if (info.getInvokedExpression() != null) { //it checks in visitRefExpr(...)
            PsiType type = info.getInvokedExpression().getType();
            checkCallApplicability(type, true, info);
        }

        checkNamedArgumentsType(info);
    }

    private void checkNamedArgumentsType(@Nonnull CallInfo<?> info) {
        if (!(info.getCall() instanceof GrCall call)) {
            return;
        }

        GrNamedArgument[] namedArguments = PsiUtil.getFirstMapNamedArguments(call);
        if (namedArguments.length == 0) {
            return;
        }

        Map<String, NamedArgumentDescriptor> map = GroovyNamedArgumentProvider.getNamedArgumentsFromAllProviders(call, null, false);
        if (map == null) {
            return;
        }

        for (GrNamedArgument namedArgument : namedArguments) {
            String labelName = namedArgument.getLabelName();

            NamedArgumentDescriptor descriptor = map.get(labelName);

            if (descriptor == null) {
                continue;
            }

            GrExpression namedArgumentExpression = namedArgument.getExpression();
            if (namedArgumentExpression == null) {
                continue;
            }

            if (getTupleInitializer(namedArgumentExpression) != null) {
                continue;
            }

            if (PsiUtil.isRawClassMemberAccess(namedArgumentExpression)) {
                continue;
            }

            PsiType expressionType =
                TypesUtil.boxPrimitiveType(namedArgumentExpression.getType(), call.getManager(), call.getResolveScope());
            if (expressionType == null) {
                continue;
            }

            if (!descriptor.checkType(expressionType, call)) {
                registerError(
                    namedArgumentExpression,
                    LocalizeValue.localizeTODO("Type of argument '" + labelName + "' can not be '" + expressionType.getPresentableText() + "'"),
                    LocalQuickFix.EMPTY_ARRAY,
                    ProblemHighlightType.GENERIC_ERROR
                );
            }
        }
    }

    @RequiredReadAction
    private void checkOperator(@Nonnull CallInfo<? extends GrBinaryExpression> info) {
        if (hasErrorElements(info.getCall())) {
            return;
        }

        if (isSpockTimesOperator(info.getCall())) {
            return;
        }

        GroovyResolveResult[] results = info.multiResolve();
        GroovyResolveResult resolveResult = info.advancedResolve();

        if (isOperatorWithSimpleTypes(info.getCall(), resolveResult)) {
            return;
        }

        if (!checkCannotInferArgumentTypes(info)) {
            return;
        }

        if (resolveResult.getElement() != null) {
            checkMethodApplicability(resolveResult, true, info);
        }
        else if (results.length > 0) {
            for (GroovyResolveResult result : results) {
                if (!checkMethodApplicability(result, false, info)) {
                    return;
                }
            }

            registerError(
                info.getElementToHighlight(),
                GroovyLocalize.methodCallIsAmbiguous(),
                LocalQuickFix.EMPTY_ARRAY,
                ProblemHighlightType.GENERIC_ERROR
            );
        }
    }

    private void highlightInapplicableMethodUsage(
        @Nonnull GroovyResolveResult methodResolveResult,
        @Nonnull CallInfo info,
        @Nonnull PsiMethod method
    ) {
        PsiClass containingClass =
            method instanceof GrGdkMethod gdkMethod ? gdkMethod.getStaticMethod().getContainingClass() : method.getContainingClass();

        PsiType[] argumentTypes = info.getArgumentTypes();
        if (containingClass == null) {
            registerCannotApplyError(method.getName(), info);
            return;
        }
        String typesString = buildArgTypesList(argumentTypes);
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(method.getProject());
        PsiClassType containingType = factory.createType(containingClass, methodResolveResult.getSubstitutor());
        String canonicalText = containingType.getInternalCanonicalText();
        LocalizeValue message = method.isConstructor()
            ? GroovyLocalize.cannotApplyConstructor(method.getName(), canonicalText, typesString)
            : GroovyLocalize.cannotApplyMethod1(method.getName(), canonicalText, typesString);

        registerError(
            info.getElementToHighlight(),
            message,
            genCastFixes(GrClosureSignatureUtil.createSignature(methodResolveResult), argumentTypes, info.getArgumentList()),
            ProblemHighlightType.GENERIC_ERROR
        );
    }

    private void highlightUnknownArgs(@Nonnull CallInfo info) {
        registerError(
            info.getElementToHighlight(),
            GroovyLocalize.cannotInferArgumentTypes(),
            LocalQuickFix.EMPTY_ARRAY,
            ProblemHighlightType.WEAK_WARNING
        );
    }

    @RequiredReadAction
    private void processAssignment(@Nonnull PsiType expectedType, @Nonnull GrExpression expression, @Nonnull PsiElement toHighlight) {
        processAssignment(expectedType, expression, toHighlight, GroovyLocalize::cannotAssign, toHighlight, ApplicableTo.ASSIGNMENT);
    }

    @RequiredReadAction
    private void processAssignment(
        @Nonnull PsiType expectedType,
        @Nonnull GrExpression expression,
        @Nonnull PsiElement toHighlight,
        @Nonnull BiFunction<Object, Object, LocalizeValue> messageTemplate,
        @Nonnull PsiElement context,
        @Nonnull ApplicableTo position
    ) {
        { // check if  current assignment is constructor call
            GrListOrMap initializer = getTupleInitializer(expression);
            if (initializer != null) {
                processConstructorCall(new GrListOrMapInfo(initializer));
                return;
            }
        }

        if (PsiUtil.isRawClassMemberAccess(expression)) {
            return;
        }
        if (checkForImplicitEnumAssigning(expectedType, expression, expression)) {
            return;
        }

        PsiType actualType = expression.getType();
        if (actualType == null) {
            return;
        }

        ConversionResult result = TypesUtil.canAssign(expectedType, actualType, context, position);
        if (result == ConversionResult.OK) {
            return;
        }

        List<LocalQuickFix> fixes = new ArrayList<>();
        {
            fixes.add(new GrCastFix(expectedType, expression));
            String varName = getLValueVarName(toHighlight);
            if (varName != null) {
                fixes.add(new GrChangeVariableType(actualType, varName));
            }
        }

        LocalizeValue message = messageTemplate.apply(actualType.getPresentableText(), expectedType.getPresentableText());
        registerError(
            toHighlight,
            message,
            fixes.toArray(new LocalQuickFix[fixes.size()]),
            result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
    }


    private void processAssignment(
        @Nonnull PsiType lType,
        @Nullable PsiType rType,
        @Nonnull GroovyPsiElement context,
        @Nonnull PsiElement elementToHighlight
    ) {
        if (rType == null) {
            return;
        }
        ConversionResult result = TypesUtil.canAssign(lType, rType, context, ApplicableTo.ASSIGNMENT);
        processResult(result, elementToHighlight, GroovyLocalize::cannotAssign, lType, rType);
    }

    protected void processAssignmentWithinMultipleAssignment(
        @Nonnull GrExpression lhs,
        @Nonnull GrExpression rhs,
        @Nonnull GrExpression context
    ) {
        PsiType targetType = lhs.getType();
        PsiType actualType = rhs.getType();
        if (targetType == null || actualType == null) {
            return;
        }

        ConversionResult result = TypesUtil.canAssignWithinMultipleAssignment(targetType, actualType, context);
        if (result == ConversionResult.OK) {
            return;
        }
        registerError(
            rhs,
            GroovyLocalize.cannotAssign(actualType.getPresentableText(), targetType.getPresentableText()),
            LocalQuickFix.EMPTY_ARRAY,
            result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
    }

    protected void processTupleAssignment(@Nonnull GrTupleExpression tupleExpression, @Nonnull GrExpression initializer) {
        GrExpression[] lValues = tupleExpression.getExpressions();
        if (initializer instanceof GrListOrMap listOrMap) {
            GrExpression[] initializers = listOrMap.getInitializers();
            for (int i = 0; i < lValues.length; i++) {
                GrExpression lValue = lValues[i];
                if (initializers.length <= i) {
                    break;
                }
                GrExpression rValue = initializers[i];
                processAssignmentWithinMultipleAssignment(lValue, rValue, tupleExpression);
            }
        }
        else {
            PsiType type = initializer.getType();
            PsiType rType = extractIterableTypeParameter(type, false);

            for (GrExpression lValue : lValues) {
                PsiType lType = lValue.getNominalType();
                // For assignments with spread dot
                if (PsiImplUtil.isSpreadAssignment(lValue)) {
                    PsiType argType = extractIterableTypeParameter(lType, false);
                    if (argType != null && rType != null) {
                        processAssignment(argType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
                    }
                    return;
                }
                if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression) lValue).resolve() instanceof GrReferenceExpression) {
                    //lvalue is not-declared variable
                    return;
                }

                if (lType != null && rType != null) {
                    processAssignment(lType, rType, tupleExpression, getExpressionPartToHighlight(lValue));
                }
            }
        }
    }

    private void processResult(
        @Nonnull ConversionResult result,
        @Nonnull PsiElement elementToHighlight,
        @Nonnull BiFunction<Object, Object, LocalizeValue> messageTemplate,
        @Nonnull PsiType lType,
        @Nonnull PsiType rType
    ) {
        if (result == ConversionResult.OK) {
            return;
        }
        registerError(
            elementToHighlight,
            messageTemplate.apply(rType.getPresentableText(), lType.getPresentableText()),
            LocalQuickFix.EMPTY_ARRAY,
            result == ConversionResult.ERROR ? ProblemHighlightType.GENERIC_ERROR : ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        );
    }

    @RequiredReadAction
    protected void processReturnValue(
        @Nonnull GrExpression expression,
        @Nonnull PsiElement context,
        @Nonnull PsiElement elementToHighlight
    ) {
        if (getTupleInitializer(expression) != null) {
            return;
        }
        PsiType returnType = PsiImplUtil.inferReturnType(expression);
        if (returnType == null || returnType == PsiType.VOID) {
            return;
        }
        processAssignment(returnType, expression, elementToHighlight, GroovyLocalize::cannotReturnType, context, ApplicableTo.RETURN_VALUE);
    }

    private void registerCannotApplyError(@Nonnull String invokedText, @Nonnull CallInfo info) {
        if (info.getArgumentTypes() == null) {
            return;
        }
        String typesString = buildArgTypesList(info.getArgumentTypes());
        registerError(
            info.getElementToHighlight(),
            GroovyLocalize.cannotApplyMethodOrClosure(invokedText, typesString),
            LocalQuickFix.EMPTY_ARRAY,
            ProblemHighlightType.GENERIC_ERROR
        );
    }

    @Override
    protected void registerError(
        @Nonnull PsiElement location,
        @Nonnull LocalizeValue description,
        @Nullable LocalQuickFix[] fixes,
        ProblemHighlightType highlightType
    ) {
        if (PsiUtil.isCompileStatic(location)) {
            // filter all errors here, error will be highlighted by annotator
            if (highlightType != ProblemHighlightType.GENERIC_ERROR) {
                super.registerError(location, description, fixes, highlightType);
            }
        }
        else if (highlightType == ProblemHighlightType.GENERIC_ERROR) {
            // if this visitor works within non-static context we will highlight all errors as warnings
            super.registerError(location, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }
        else {
            // if this visitor works within static context errors will be highlighted as errors by annotator, warnings will be highlighted as warnings here
            super.registerError(location, description, fixes, highlightType);
        }
    }

    @Override
    @RequiredReadAction
    public void visitEnumConstant(GrEnumConstant enumConstant) {
        super.visitEnumConstant(enumConstant);
        GrEnumConstantInfo info = new GrEnumConstantInfo(enumConstant);
        processConstructorCall(info);
        checkNamedArgumentsType(info);
    }

    @Override
    @RequiredReadAction
    public void visitReturnStatement(GrReturnStatement returnStatement) {
        super.visitReturnStatement(returnStatement);
        GrExpression value = returnStatement.getReturnValue();
        if (value != null) {
            processReturnValue(value, returnStatement, returnStatement.getReturnWord());
        }
    }

    @Override
    @RequiredReadAction
    public void visitThrowStatement(GrThrowStatement throwStatement) {
        super.visitThrowStatement(throwStatement);
        GrExpression exception = throwStatement.getException();
        if (exception == null) {
            return;
        }
        PsiElement throwWord = throwStatement.getFirstChild();
        processAssignment(
            PsiType.getJavaLangThrowable(
                throwStatement.getManager(),
                throwStatement.getResolveScope()
            ),
            exception,
            throwWord
        );
    }

    @Override
    @RequiredReadAction
    public void visitExpression(GrExpression expression) {
        super.visitExpression(expression);
        if (isImplicitReturnStatement(expression)) {
            processReturnValue(expression, expression, expression);
        }
    }

    @Override
    @RequiredReadAction
    public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        super.visitMethodCallExpression(methodCallExpression);
        checkMethodCall(new GrMethodCallInfo(methodCallExpression));
    }

    @Override
    @RequiredReadAction
    public void visitNewExpression(GrNewExpression newExpression) {
        super.visitNewExpression(newExpression);
        if (newExpression.getArrayCount() > 0) {
            return;
        }

        GrCodeReferenceElement refElement = newExpression.getReferenceElement();
        if (refElement == null) {
            return;
        }

        GrNewExpressionInfo info = new GrNewExpressionInfo(newExpression);
        processConstructorCall(info);
    }

    @Override
    @RequiredReadAction
    public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        super.visitApplicationStatement(applicationStatement);
        checkMethodCall(new GrMethodCallInfo(applicationStatement));
    }

    @Override
    @RequiredReadAction
    public void visitAssignmentExpression(GrAssignmentExpression assignment) {
        super.visitAssignmentExpression(assignment);

        GrExpression lValue = assignment.getLValue();
        if (lValue instanceof GrIndexProperty) {
            return;
        }
        if (!PsiUtil.mightBeLValue(lValue)) {
            return;
        }

        IElementType opToken = assignment.getOperationTokenType();
        if (opToken != GroovyTokenTypes.mASSIGN) {
            return;
        }

        GrExpression rValue = assignment.getRValue();
        if (rValue == null) {
            return;
        }

        if (lValue instanceof GrReferenceExpression && ((GrReferenceExpression) lValue).resolve() instanceof GrReferenceExpression) {
            //lvalue is not-declared variable
            return;
        }

        if (lValue instanceof GrTupleExpression) {
            processTupleAssignment(((GrTupleExpression) lValue), rValue);
        }
        else {
            PsiType lValueNominalType = lValue.getNominalType();
            PsiType targetType = PsiImplUtil.isSpreadAssignment(lValue)
                ? extractIterableTypeParameter(lValueNominalType, false)
                : lValueNominalType;
            if (targetType != null) {
                processAssignment(targetType, rValue, lValue, GroovyLocalize::cannotAssign, assignment, ApplicableTo.ASSIGNMENT);
            }
        }
    }

    @Override
    @RequiredReadAction
    public void visitBinaryExpression(GrBinaryExpression binary) {
        super.visitBinaryExpression(binary);
        checkOperator(new GrBinaryExprInfo(binary));
    }

    @Override
    public void visitCastExpression(GrTypeCastExpression expression) {
        super.visitCastExpression(expression);

        GrExpression operand = expression.getOperand();
        if (operand == null) {
            return;
        }
        PsiType actualType = operand.getType();
        if (actualType == null) {
            return;
        }

        if (expression.getCastTypeElement() == null) {
            return;
        }
        PsiType expectedType = expression.getCastTypeElement().getType();

        ConversionResult result = TypesUtil.canCast(expectedType, actualType, expression);
        if (result == ConversionResult.OK) {
            return;
        }
        ProblemHighlightType highlightType = result == ConversionResult.ERROR
            ? ProblemHighlightType.GENERIC_ERROR
            : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        registerError(
            expression,
            GroovyLocalize.cannotCast(actualType.getPresentableText(), expectedType.getPresentableText()),
            LocalQuickFix.EMPTY_ARRAY,
            highlightType
        );
    }

    @Override
    @RequiredReadAction
    public void visitIndexProperty(GrIndexProperty expression) {
        super.visitIndexProperty(expression);
        checkIndexProperty(new GrIndexPropertyInfo(expression));
    }

    /**
     * Handles method default values.
     */
    @Override
    @RequiredReadAction
    public void visitMethod(GrMethod method) {
        super.visitMethod(method);

        PsiTypeParameter[] parameters = method.getTypeParameters();
        Map<PsiTypeParameter, PsiType> map = new HashMap<>();
        for (PsiTypeParameter parameter : parameters) {
            PsiClassType[] types = parameter.getSuperTypes();
            PsiType bound = PsiIntersectionType.createIntersection(types);
            PsiWildcardType wildcardType = PsiWildcardType.createExtends(method.getManager(), bound);
            map.put(parameter, wildcardType);
        }
        PsiSubstitutor substitutor = PsiSubstitutorImpl.createSubstitutor(map);

        for (GrParameter parameter : method.getParameterList().getParameters()) {
            GrExpression initializer = parameter.getInitializerGroovy();
            if (initializer == null) {
                continue;
            }
            PsiType targetType = parameter.getType();
            processAssignment(
                substitutor.substitute(targetType),
                initializer,
                parameter.getNameIdentifierGroovy(),
                GroovyLocalize::cannotAssign,
                method,
                ApplicableTo.ASSIGNMENT
            );
        }
    }

    @Override
    @RequiredReadAction
    public void visitConstructorInvocation(GrConstructorInvocation invocation) {
        super.visitConstructorInvocation(invocation);
        GrConstructorInvocationInfo info = new GrConstructorInvocationInfo(invocation);
        processConstructorCall(info);
        checkNamedArgumentsType(info);
    }

    @Override
    public void visitParameterList(GrParameterList parameterList) {
        super.visitParameterList(parameterList);
        if (!(parameterList.getParent() instanceof GrClosableBlock closableBlock)) {
            return;
        }

        GrParameter[] parameters = parameterList.getParameters();
        if (parameters.length > 0) {
            List<PsiType[]> signatures = ClosureParamsEnhancer.findFittingSignatures(closableBlock);
            List<PsiType> paramTypes = ContainerUtil.map(parameters, parameter -> parameter.getType());

            if (signatures.size() > 1) {
                PsiType[] fittingSignature = ContainerUtil.find(
                    signatures,
                    types -> {
                        for (int i = 0; i < types.length; i++) {
                            if (!typesAreEqual(types[i], paramTypes.get(i), parameterList)) {
                                return false;
                            }
                        }
                        return true;
                    }
                );
                if (fittingSignature == null) {
                    registerError(
                        parameterList,
                        GroovyInspectionLocalize.noApplicableSignatureFound(),
                        null,
                        ProblemHighlightType.GENERIC_ERROR
                    );
                }
            }
            else if (signatures.size() == 1) {
                PsiType[] types = signatures.get(0);
                for (int i = 0; i < types.length; i++) {
                    GrTypeElement typeElement = parameters[i].getTypeElementGroovy();
                    if (typeElement == null) {
                        continue;
                    }
                    PsiType expected = types[i];
                    PsiType actual = paramTypes.get(i);
                    if (!typesAreEqual(expected, actual, parameterList)) {
                        registerError(
                            typeElement,
                            GroovyInspectionLocalize.expectedType0(expected.getPresentableText(), actual.getPresentableText()),
                            null,
                            ProblemHighlightType.GENERIC_ERROR
                        );
                    }
                }
            }
        }
    }

    @Override
    public void visitForInClause(GrForInClause forInClause) {
        super.visitForInClause(forInClause);
        GrVariable variable = forInClause.getDeclaredVariable();
        GrExpression iterated = forInClause.getIteratedExpression();
        if (variable == null || iterated == null) {
            return;
        }

        PsiType iteratedType = ClosureParameterEnhancer.findTypeForIteration(iterated, forInClause);
        if (iteratedType == null) {
            return;
        }
        PsiType targetType = variable.getType();

        processAssignment(targetType, iteratedType, forInClause, variable.getNameIdentifierGroovy());
    }

    @Override
    @RequiredReadAction
    public void visitVariable(GrVariable variable) {
        super.visitVariable(variable);

        PsiType varType = variable.getType();
        PsiElement parent = variable.getParent();

        if (variable instanceof GrParameter param && param.getDeclarationScope() instanceof GrMethod
            || parent instanceof GrForInClause) {
            return;
        }
        else if (parent instanceof GrVariableDeclaration tuple && tuple.isTuple()) {
            //check tuple assignment:  def (int x, int y) = foo()
            GrExpression initializer = tuple.getTupleInitializer();
            if (initializer == null) {
                return;
            }
            if (!(initializer instanceof GrListOrMap)) {
                PsiType type = initializer.getType();
                if (type == null) {
                    return;
                }
                PsiType valueType = extractIterableTypeParameter(type, false);
                processAssignment(varType, valueType, tuple, variable.getNameIdentifierGroovy());
                return;
            }
        }

        GrExpression initializer = variable.getInitializerGroovy();
        if (initializer == null) {
            return;
        }

        processAssignment(
            varType,
            initializer,
            variable.getNameIdentifierGroovy(),
            GroovyLocalize::cannotAssign,
            variable,
            ApplicableTo.ASSIGNMENT
        );
    }

    @Override
    protected void registerError(@Nonnull PsiElement location, ProblemHighlightType highlightType, Object... args) {
        registerError(location, (LocalizeValue) args[0], LocalQuickFix.EMPTY_ARRAY, highlightType);
    }
}
