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
import com.intellij.java.impl.refactoring.introduceParameter.ChangedMethodCallInfo;
import com.intellij.java.impl.refactoring.introduceParameter.ExpressionConverter;
import com.intellij.java.impl.refactoring.introduceParameter.ExternalUsageInfo;
import com.intellij.java.impl.refactoring.introduceParameter.InternalUsageInfo;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.impl.codeInsight.ChangeContextUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.usage.UsageViewUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import consulo.util.collection.primitive.ints.IntList;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.java2groovy.FieldConflictsResolver;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.java2groovy.OldReferencesResolver;
import org.jetbrains.plugins.groovy.impl.refactoring.util.AnySupers;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;

/**
 * @author Medvedev Max
 */
public class GrIntroduceClosureParameterProcessor extends BaseRefactoringProcessor {
    private static final Logger LOG = Logger.getInstance(GrIntroduceClosureParameterProcessor.class);

    private final GrIntroduceParameterSettings mySettings;
    private final GrClosableBlock toReplaceIn;
    private final PsiElement toSearchFor;
    private final GrExpressionWrapper myParameterInitializer;
    private final GroovyPsiElementFactory myFactory = GroovyPsiElementFactory.getInstance(myProject);

    public GrIntroduceClosureParameterProcessor(@Nonnull GrIntroduceParameterSettings settings) {
        super(settings.getProject(), null);
        mySettings = settings;

        toReplaceIn = (GrClosableBlock)mySettings.getToReplaceIn();
        toSearchFor = mySettings.getToSearchFor();

        StringPartInfo info = settings.getStringPartInfo();
        GrExpression expression = info != null ? info.createLiteralFromSelected() : mySettings.getExpression();
        myParameterInitializer = new GrExpressionWrapper(expression);
    }

    @Override
    @Nonnull
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new UsageViewDescriptorAdapter() {
            @Nonnull
            @Override
            public PsiElement[] getElements() {
                return new PsiElement[]{toSearchFor != null ? toSearchFor : toReplaceIn};
            }

            @Override
            public String getProcessedElementsHeader() {
                return GroovyRefactoringLocalize.introduceClosureParameterElementsHeader().get();
            }
        };
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(@Nonnull SimpleReference<UsageInfo[]> refUsages) {
        UsageInfo[] usagesIn = refUsages.get();
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();

        if (!mySettings.generateDelegate()) {
            detectAccessibilityConflicts(usagesIn, conflicts);
        }

        GrExpression expression = mySettings.getExpression();
        if (expression != null && toSearchFor instanceof PsiMember) {
            AnySupers anySupers = new AnySupers();
            expression.accept(anySupers);
            if (anySupers.containsSupers()) {
                PsiElement containingClass = PsiUtil.getFileOrClassContext(toReplaceIn);
                for (UsageInfo usageInfo : usagesIn) {
                    if (usageInfo.getElement() instanceof PsiMethod || usageInfo instanceof InternalUsageInfo
                        || PsiTreeUtil.isAncestor(containingClass, usageInfo.getElement(), false)) {
                        continue;
                    }

                    conflicts.putValue(
                        expression,
                        RefactoringLocalize.parameterInitializerContains0ButNotAllCallsToMethodAreInItsClass(
                            CommonRefactoringUtil.htmlEmphasize(PsiKeyword.SUPER)
                        )
                    );
                    break;
                }
            }
        }

        //todo
        //for (IntroduceParameterMethodUsagesProcessor processor : IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions()) {
        //    processor.findConflicts(this, refUsages.get(), conflicts);
        //}

        return showConflicts(conflicts, usagesIn);
    }

    private void detectAccessibilityConflicts(UsageInfo[] usageArray, MultiMap<PsiElement, LocalizeValue> conflicts) {
        //todo whole method
        GrExpression expression = mySettings.getExpression();
        if (expression == null) {
            return;
        }

        GroovyIntroduceParameterUtil.detectAccessibilityConflicts(
            expression,
            usageArray,
            conflicts,
            mySettings.replaceFieldsWithGetters() != IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
            myProject
        );
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected UsageInfo[] findUsages() {
        ArrayList<UsageInfo> result = new ArrayList<>();

        if (!mySettings.generateDelegate() && toSearchFor != null) {
            Collection<PsiReference> refs;
            if (toSearchFor instanceof GrField field) {
                refs = ReferencesSearch.search(field).findAll();
                GrAccessorMethod[] getters = field.getGetters();
                for (GrAccessorMethod getter : getters) {
                    refs.addAll(MethodReferencesSearch.search(getter, getter.getResolveScope(), true).findAll());
                }
            }
            else if (toSearchFor instanceof GrVariable variable) {
                refs = findUsagesForLocal(toReplaceIn, variable);
            }
            else {
                refs = ReferencesSearch.search(toSearchFor).findAll();
            }

            for (PsiReference ref1 : refs) {
                PsiElement ref = ref1.getElement();
                if (!PsiTreeUtil.isAncestor(toReplaceIn, ref, false)) {
                    result.add(new ExternalUsageInfo(ref));
                }
                else {
                    result.add(new ChangedMethodCallInfo(ref));
                }
            }

            if (toSearchFor instanceof GrVariable variable && !variable.hasModifierProperty(PsiModifier.FINAL)) {
                setPreviewUsages(true);
            }
        }

        if (mySettings.replaceAllOccurrences()) {
            PsiElement[] exprs = GroovyIntroduceParameterUtil.getOccurrences(mySettings);
            for (PsiElement expr : exprs) {
                result.add(new InternalUsageInfo(expr));
            }
        }
        else {
            if (mySettings.getExpression() != null) {
                result.add(new InternalUsageInfo(mySettings.getExpression()));
            }
        }

        UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
        return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    private static Collection<PsiReference> findUsagesForLocal(GrClosableBlock initializer, GrVariable var) {
        Instruction[] flow = ControlFlowUtils.findControlFlowOwner(initializer).getControlFlow();
        ArrayList<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(flow, var);

        Instruction writeInstr = null;

        PsiElement parent = initializer.getParent();
        if (parent instanceof GrVariable) {
            writeInstr = ContainerUtil.find(flow, instruction -> instruction.getElement() == var);
        }
        else if (parent instanceof GrAssignmentExpression assignment) {
            GrReferenceExpression refExpr = (GrReferenceExpression)assignment.getLValue();
            Instruction instruction = ContainerUtil.find(flow, instruction1 -> instruction1.getElement() == refExpr);

            LOG.assertTrue(instruction != null);
            BitSet prev = writes.get(instruction.num());
            if (prev.cardinality() == 1) {
                writeInstr = flow[prev.nextSetBit(0)];
            }
        }

        LOG.assertTrue(writeInstr != null);

        Collection<PsiReference> result = new ArrayList<>();
        for (Instruction instruction : flow) {
            if (!(instruction instanceof ReadWriteVariableInstruction readWriteVarInsn)) {
                continue;
            }
            if (readWriteVarInsn.isWrite()) {
                continue;
            }

            PsiElement element = instruction.getElement();
            if (element instanceof GrVariable && element != var) {
                continue;
            }
            if (!(element instanceof GrReferenceExpression ref)) {
                continue;
            }

            if (ref.isQualified() || ref.resolve() != var) {
                continue;
            }

            BitSet prev = writes.get(instruction.num());
            if (prev.cardinality() == 1 && prev.get(writeInstr.num())) {
                result.add(ref);
            }
        }

        return result;
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(@Nonnull UsageInfo[] usages) {
        processExternalUsages(usages, mySettings, myParameterInitializer.getExpression());
        processClosure(usages, mySettings);

        GrVariable var = mySettings.getVar();
        if (var != null && mySettings.removeLocalVariable()) {
            var.delete();
        }
    }

    @RequiredWriteAction
    public static void processClosure(UsageInfo[] usages, GrIntroduceParameterSettings settings) {
        changeSignature((GrClosableBlock)settings.getToReplaceIn(), settings);
        processInternalUsages(usages, settings);
    }

    @RequiredWriteAction
    private static void processInternalUsages(UsageInfo[] usages, GrIntroduceParameterSettings settings) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
        // Replacing expression occurrences
        for (UsageInfo usage : usages) {
            if (usage instanceof ChangedMethodCallInfo) {
                PsiElement element = usage.getElement();

                processChangedMethodCall(element, settings);
            }
            else if (usage instanceof InternalUsageInfo) {
                PsiElement element = usage.getElement();
                if (element == null) {
                    continue;
                }
                GrExpression newExpr = factory.createExpressionFromText(settings.getName());
                if (element instanceof GrExpression expression) {
                    expression.replaceWithExpression(newExpr, true);
                }
                else {
                    element.replace(newExpr);
                }
            }
        }

        StringPartInfo info = settings.getStringPartInfo();
        if (info != null) {
            GrExpression expr = info.replaceLiteralWithConcatenation(settings.getName());
            Editor editor = PsiUtilBase.findEditor(expr);
            if (editor != null) {
                editor.getSelectionModel().removeSelection();
                editor.getCaretModel().moveToOffset(expr.getTextRange().getEndOffset());
            }
        }
    }

    @RequiredWriteAction
    public static void processExternalUsages(
        UsageInfo[] usages,
        GrIntroduceParameterSettings settings,
        PsiElement expression
    ) {
        for (UsageInfo usage : usages) {
            if (usage instanceof ExternalUsageInfo) {
                processExternalUsage(usage, settings, expression);
            }
        }
    }

    @RequiredWriteAction
    private static void changeSignature(GrClosableBlock block, GrIntroduceParameterSettings settings) {
        String name = settings.getName();
        FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(name, block);

        GrParameter[] parameters = block.getParameters();
        IntList parametersToRemove = settings.parametersToRemove();
        for (int i = parametersToRemove.size() - 1; i >= 0; i--) {
            int paramNum = parametersToRemove.get(0);
            try {
                PsiParameter param = parameters[paramNum];
                param.delete();
            }
            catch (IncorrectOperationException e) {
                LOG.error(e);
            }
        }

        PsiType type = settings.getSelectedType();
        String typeText = type == null ? null : type.getCanonicalText();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
        GrParameter parameter = factory.createParameter(name, typeText, block);
        parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, settings.declareFinal());

        GrParameterList parameterList = block.getParameterList();
        PsiParameter anchorParameter =
            GroovyIntroduceParameterUtil.getAnchorParameter(parameterList, block.isVarArgs());
        parameter = (GrParameter)parameterList.addAfter(parameter, anchorParameter);

        if (block.getArrow() == null) {
            PsiElement arrow = block.addAfter(
                factory.createClosureFromText("{->}").getArrow().copy(),
                parameterList
            );
            PsiElement child = block.getFirstChild().getNextSibling();
            if (PsiImplUtil.isWhiteSpaceOrNls(child)) {
                String text = child.getText();
                child.delete();
                block.addAfter(factory.createLineTerminator(text), arrow);
            }
        }
        JavaCodeStyleManager.getInstance(parameter.getProject()).shortenClassReferences(parameter);

        fieldConflictsResolver.fix();
    }

    @RequiredWriteAction
    private static void processExternalUsage(UsageInfo usage, GrIntroduceParameterSettings settings, PsiElement expression) {
        PsiElement element = usage.getElement();
        GrCall callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
        if (callExpression == null && element.getParent() instanceof GrReferenceExpression refExpr && element == refExpr.getQualifier()
            && "call".equals(refExpr.getReferenceName())) {
            callExpression = GroovyRefactoringUtil.getCallExpressionByMethodReference(refExpr);
        }

        if (callExpression == null) {
            return;
        }

        //LOG.assertTrue(callExpression != null);

        //check for x.getFoo()(args)
        if (callExpression instanceof GrMethodCall methodCall
            && methodCall.getInvokedExpression() instanceof GrReferenceExpression refExpr) {
            GroovyResolveResult result = refExpr.advancedResolve();
            if (result.getElement() instanceof GrAccessorMethod && !result.isInvokedOnProperty()
                && methodCall.getParent() instanceof GrCall call) {
                callExpression = call;
            }
        }

        GrArgumentList argList = callExpression.getArgumentList();
        LOG.assertTrue(argList != null);
        GrExpression[] oldArgs = argList.getExpressionArguments();

        GrClosableBlock toReplaceIn = (GrClosableBlock)settings.getToReplaceIn();
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());

        GrExpression anchor = getAnchorForArgument(oldArgs, toReplaceIn.isVarArgs(), toReplaceIn.getParameterList());

        GrClosureSignature signature = GrClosureSignatureUtil.createSignature(callExpression);
        if (signature == null) {
            signature = GrClosureSignatureUtil.createSignature(toReplaceIn);
        }

        GrClosureSignatureUtil.ArgInfo<PsiElement>[] actualArgs = GrClosureSignatureUtil.mapParametersToArguments(
            signature,
            callExpression.getNamedArguments(),
            callExpression.getExpressionArguments(),
            callExpression.getClosureArguments(),
            callExpression,
            true,
            true
        );

        if (PsiTreeUtil.isAncestor(toReplaceIn, callExpression, false)) {
            argList.addAfter(factory.createExpressionFromText(settings.getName()), anchor);
        }
        else {
            PsiElement initializer = ExpressionConverter.getExpression(expression, GroovyLanguage.INSTANCE, settings.getProject());
            LOG.assertTrue(initializer instanceof GrExpression);

            GrExpression newArg = GroovyIntroduceParameterUtil.addClosureToCall(initializer, argList);
            if (newArg == null) {
                PsiElement dummy = argList.addAfter(factory.createExpressionFromText("1"), anchor);
                newArg = ((GrExpression)dummy).replaceWithExpression((GrExpression)initializer, true);
            }
            new OldReferencesResolver(
                callExpression,
                newArg,
                toReplaceIn,
                settings.replaceFieldsWithGetters(),
                initializer,
                signature,
                actualArgs,
                toReplaceIn.getParameters()
            ).resolve();
            ChangeContextUtil.clearContextInfo(initializer);

            //newArg can be replaced by OldReferenceResolve
            if (newArg.isValid()) {
                JavaCodeStyleManager.getInstance(newArg.getProject()).shortenClassReferences(newArg);
                CodeStyleManager.getInstance(settings.getProject()).reformat(newArg);
            }
        }

        if (actualArgs == null) {
            GroovyIntroduceParameterUtil.removeParamsFromUnresolvedCall(
                callExpression,
                toReplaceIn.getParameters(),
                settings.parametersToRemove()
            );
        }
        else {
            GroovyIntroduceParameterUtil.removeParametersFromCall(actualArgs, settings.parametersToRemove());
        }

        if (argList.getAllArguments().length == 0 && PsiImplUtil.hasClosureArguments(callExpression)) {
            GrArgumentList emptyArgList = ((GrMethodCallExpression)factory.createExpressionFromText("foo{}")).getArgumentList();
            LOG.assertTrue(emptyArgList != null);
            argList.replace(emptyArgList);
        }
    }

    @Nullable
    private static GrExpression getAnchorForArgument(
        GrExpression[] oldArgs,
        boolean isVarArg,
        PsiParameterList parameterList
    ) {
        if (!isVarArg) {
            return ArrayUtil.getLastElement(oldArgs);
        }

        PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length > oldArgs.length) {
            return ArrayUtil.getLastElement(oldArgs);
        }

        int lastNonVararg = parameters.length - 2;
        return lastNonVararg >= 0 ? oldArgs[lastNonVararg] : null;
    }

    @RequiredWriteAction
    private GrClosableBlock generateDelegateClosure(GrClosableBlock originalClosure, GrVariable anchor, String newName) {
        GrClosableBlock result;
        if (originalClosure.hasParametersSection()) {
            result = myFactory.createClosureFromText("{->}", anchor);
            GrParameterList parameterList = (GrParameterList)originalClosure.getParameterList().copy();
            result.getParameterList().replace(parameterList);
        }
        else {
            result = myFactory.createClosureFromText("{}", anchor);
        }

        StringBuilder call = new StringBuilder();
        call.append(newName).append('(');

        GrParameter[] parameters = result.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (!mySettings.parametersToRemove().contains(i)) {
                call.append(parameters[i].getName()).append(", ");
            }
        }
        call.append(myParameterInitializer.getText());
        call.append(")");

        GrStatement statement = myFactory.createStatementFromText(call.toString());
        result.addStatementBefore(statement, null);
        return result;
    }

    @RequiredWriteAction
    private GrVariableDeclaration insertDeclaration(GrVariable original, GrVariableDeclaration declaration) {
        if (original instanceof GrField field) {
            PsiClass containingClass = field.getContainingClass();
            LOG.assertTrue(containingClass != null);
            return (GrVariableDeclaration)containingClass.addBefore(declaration, original.getParent());
        }

        GrStatementOwner block;
        if (original instanceof PsiParameter param) {
            PsiElement container = param.getParent().getParent();
            if (container instanceof GrMethod method) {
                block = method.getBlock();
            }
            else if (container instanceof GrClosableBlock closableBlock) {
                block = closableBlock;
            }
            else if (container instanceof GrForStatement forStmt) {
                GrStatement body = forStmt.getBody();
                if (body instanceof GrBlockStatement blockStmt) {
                    block = blockStmt.getBlock();
                }
                else {
                    GrBlockStatement blockStatement = myFactory.createBlockStatement();
                    LOG.assertTrue(blockStatement != null);
                    if (body != null) {
                        blockStatement.getBlock().addStatementBefore((GrStatement)body.copy(), null);
                        blockStatement = (GrBlockStatement)body.replace(blockStatement);
                    }
                    else {
                        blockStatement = (GrBlockStatement)forStmt.add(blockStatement);
                    }
                    block = blockStatement.getBlock();
                }
            }
            else {
                throw new IncorrectOperationException();
            }

            LOG.assertTrue(block != null);
            return (GrVariableDeclaration)block.addStatementBefore(declaration, null);
        }

        PsiElement parent = original.getParent();
        LOG.assertTrue(parent instanceof GrVariableDeclaration);

        PsiElement grandparent = parent.getParent();

        if (grandparent instanceof GrIfStatement ifStmt) {
            if (ifStmt.getThenBranch() == parent) {
                block = ifStmt.replaceThenBranch(myFactory.createBlockStatement()).getBlock();
            }
            else {
                block = ifStmt.replaceElseBranch(myFactory.createBlockStatement()).getBlock();
            }
            parent = block.addStatementBefore(((GrVariableDeclaration)parent), null);
        }
        else if (grandparent instanceof GrLoopStatement loopStmt) {
            block = loopStmt.replaceBody(myFactory.createBlockStatement()).getBlock();
            parent = block.addStatementBefore(loopStmt, null);
        }
        else {
            LOG.assertTrue(grandparent instanceof GrStatementOwner);
            block = (GrStatementOwner)grandparent;
        }

        return (GrVariableDeclaration)block.addStatementBefore(declaration, (GrStatement)parent);
    }

    @RequiredWriteAction
    private static void processChangedMethodCall(PsiElement element, GrIntroduceParameterSettings settings) {
        if (element.getParent() instanceof GrMethodCallExpression methodCall) {
            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(settings.getProject());
            GrExpression expression = factory.createExpressionFromText(settings.getName(), null);
            GrArgumentList argList = methodCall.getArgumentList();
            PsiElement[] exprs = argList.getAllArguments();

            if (exprs.length > 0) {
                argList.addAfter(expression, exprs[exprs.length - 1]);
            }
            else {
                argList.add(expression);
            }

            removeParametersFromCall(methodCall, settings);
        }
        else {
            LOG.error(element.getParent());
        }
    }

    @RequiredWriteAction
    private static void removeParametersFromCall(GrMethodCallExpression methodCall, GrIntroduceParameterSettings settings) {
        GroovyResolveResult resolveResult = methodCall.advancedResolve();
        PsiElement resolved = resolveResult.getElement();
        LOG.assertTrue(resolved instanceof PsiMethod);
        GrClosureSignature signature = GrClosureSignatureUtil.createSignature(
            (PsiMethod)resolved,
            resolveResult.getSubstitutor()
        );
        GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, methodCall);
        LOG.assertTrue(argInfos != null);
        settings.parametersToRemove().forEach(value -> {
            List<PsiElement> args = argInfos[value].args;
            for (PsiElement arg : args) {
                arg.delete();
            }
        });
    }

    @Nonnull
    @Override
    @RequiredReadAction
    protected LocalizeValue getCommandName() {
        return RefactoringLocalize.introduceParameterCommand(DescriptiveNameUtil.getDescriptiveName(mySettings.getToReplaceIn()));
    }
}
