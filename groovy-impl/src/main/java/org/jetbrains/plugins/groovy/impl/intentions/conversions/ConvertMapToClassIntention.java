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
package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import com.intellij.java.impl.codeInsight.intention.impl.CreateClassDialog;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.awt.UIUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.impl.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.impl.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMapToClassIntention extends Intention {
    private static final Logger LOG = Logger.getInstance(ConvertMapToClassIntention.class);

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.convertMapToClassIntentionName();
    }

    @Override
    protected void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException {
        GrListOrMap map = (GrListOrMap) element;
        GrNamedArgument[] namedArguments = map.getNamedArguments();
        LOG.assertTrue(map.getInitializers().length == 0);
        PsiFile file = map.getContainingFile();
        String packageName = file instanceof GroovyFileBase ? ((GroovyFileBase) file).getPackageName() : "";

        CreateClassDialog dialog = new CreateClassDialog(
            project,
            GroovyLocalize.createClassFamilyName(),
            "",
            packageName,
            GrCreateClassKind.CLASS,
            true,
            ModuleUtilCore.findModuleForPsiElement(element)
        );
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
            return;
        }

        boolean replaceReturnType = checkForReturnFromMethod(map);
        boolean variableDeclaration = checkForVariableDeclaration(map);
        GrParameter methodParameter = checkForMethodParameter(map);

        String qualifiedClassName = dialog.getClassName();
        String selectedPackageName = StringUtil.getPackageName(qualifiedClassName);
        String shortName = StringUtil.getShortName(qualifiedClassName);

        GrTypeDefinition typeDefinition = createClass(project, namedArguments, selectedPackageName, shortName);
        PsiClass generatedClass = CreateClassActionBase.createClassByType(
            dialog.getTargetDirectory(),
            typeDefinition.getName(),
            PsiManager.getInstance(project),
            map,
            GroovyTemplates.GROOVY_CLASS,
            true
        );
        PsiClass replaced = (PsiClass) generatedClass.replace(typeDefinition);
        replaceMapWithClass(project, map, replaced, replaceReturnType, variableDeclaration, methodParameter);
    }

    public static void replaceMapWithClass(
        Project project,
        GrListOrMap map,
        PsiClass generatedClass,
        boolean replaceReturnType,
        boolean variableDeclaration,
        GrParameter parameter
    ) {
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(generatedClass);

        String text = map.getText();
        int begin = 0;
        int end = text.length();
        if (text.startsWith("[")) {
            begin++;
        }
        if (text.endsWith("]")) {
            end--;
        }
        GrExpression newExpression = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("new " +
            "" + generatedClass.getQualifiedName() + "(" + text
            .substring(begin, end) + ")");
        GrExpression replacedNewExpression = ((GrExpression) map.replace(newExpression));

        if (replaceReturnType) {
            PsiType type = replacedNewExpression.getType();
            GrMethod method = PsiTreeUtil.getParentOfType(replacedNewExpression, GrMethod.class, true,
                GrClosableBlock.class
            );
            LOG.assertTrue(method != null);
            GrReferenceAdjuster.shortenAllReferencesIn(method.setReturnType(type));
        }

        if (variableDeclaration) {
            PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
            ((GrVariable) parent).setType(replacedNewExpression.getType());
        }
        if (parameter != null) {
            parameter.setType(newExpression.getType());
        }

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacedNewExpression);

        IntentionUtils.positionCursor(project, generatedClass.getContainingFile(), generatedClass);
    }

    public static boolean checkForReturnFromMethod(GrExpression replacedNewExpression) {
        PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
        GrMethod method = PsiTreeUtil.getParentOfType(replacedNewExpression, GrMethod.class, true, GrClosableBlock.class);
        if (method == null) {
            return false;
        }

        if (!(parent instanceof GrReturnStatement)) { //check for return expression
            List<GrStatement> returns = ControlFlowUtils.collectReturns(method.getBlock());
            PsiElement expr = PsiUtil.skipParentheses(replacedNewExpression, true);
            if (!(returns.contains(expr))) {
                return false;
            }
        }
        return !(!ApplicationManager.getApplication().isUnitTestMode() && Messages.showYesNoDialog(
                replacedNewExpression.getProject(),
                GroovyIntentionLocalize.doYouWantToChangeMethodReturnType(method.getName()).get(),
                GroovyIntentionLocalize.convertMapToClassIntentionName().get(),
                UIUtil.getQuestionIcon()
            ) != Messages.YES);
    }

    public static boolean checkForVariableDeclaration(GrExpression replacedNewExpression) {
        PsiElement parent = PsiUtil.skipParentheses(replacedNewExpression.getParent(), true);
        if (parent instanceof GrVariable &&
            !(parent instanceof GrField) &&
            !(parent instanceof GrParameter) &&
            ((GrVariable) parent).getDeclaredType() != null &&
            replacedNewExpression.getType() != null) {
            if (ApplicationManager.getApplication().isUnitTestMode() || Messages.showYesNoDialog(
                replacedNewExpression
                    .getProject(),
                GroovyIntentionLocalize.doYouWantToChangeVariableType(((GrVariable) parent).getName()).get(),
                GroovyIntentionLocalize.convertMapToClassIntentionName().get(),
                UIUtil.getQuestionIcon()
            ) == Messages.YES) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static GrParameter getParameterByArgument(GrExpression arg) {
        PsiElement parent = PsiUtil.skipParentheses(arg.getParent(), true);
        if (!(parent instanceof GrArgumentList)) {
            return null;
        }
        GrArgumentList argList = (GrArgumentList) parent;

        parent = parent.getParent();
        if (!(parent instanceof GrMethodCall)) {
            return null;
        }

        GrMethodCall methodCall = (GrMethodCall) parent;
        GrExpression expression = methodCall.getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) {
            return null;
        }

        GroovyResolveResult resolveResult = ((GrReferenceExpression) expression).advancedResolve();
        if (resolveResult == null) {
            return null;
        }

        GrClosableBlock[] closures = methodCall.getClosureArguments();
        Map<GrExpression, Pair<PsiParameter, PsiType>> mapToParams = GrClosureSignatureUtil
            .mapArgumentsToParameters(resolveResult, arg, false, false, argList.getNamedArguments(),
                argList.getExpressionArguments(), closures
            );
        if (mapToParams == null) {
            return null;
        }

        Pair<PsiParameter, PsiType> parameterPair = mapToParams.get(arg);
        PsiParameter parameter = parameterPair == null ? null : parameterPair.getFirst();

        return parameter instanceof GrParameter ? ((GrParameter) parameter) : null;
    }

    @Nullable
    public static GrParameter checkForMethodParameter(GrExpression map) {
        GrParameter parameter = getParameterByArgument(map);
        if (parameter == null) {
            return null;
        }
        PsiElement parent = parameter.getParent().getParent();
        if (!(parent instanceof PsiMethod)) {
            return null;
        }
        PsiMethod method = (PsiMethod) parent;
        if (ApplicationManager.getApplication().isUnitTestMode() || Messages.showYesNoDialog(
            map.getProject(),
            GroovyIntentionLocalize.doYouWantToChangeTypeOfParameterInMethod(parameter.getName(), method.getName()).get(),
            GroovyIntentionLocalize.convertMapToClassIntentionName().get(),
            UIUtil.getQuestionIcon()
        ) == Messages.YES) {
            return parameter;
        }
        return null;
    }


    public static GrTypeDefinition createClass(
        Project project,
        GrNamedArgument[] namedArguments,
        String packageName,
        String className
    ) {
        StringBuilder classText = new StringBuilder();
        if (!packageName.isEmpty()) {
            classText.append("package ").append(packageName).append('\n');
        }
        classText.append("class ").append(className).append(" {\n");
        for (GrNamedArgument argument : namedArguments) {
            String fieldName = argument.getLabelName();
            GrExpression expression = argument.getExpression();
            LOG.assertTrue(expression != null);

            PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(expression.getType());
            if (type != null) {
                classText.append(type.getCanonicalText());
            }
            else {
                classText.append(GrModifier.DEF);
            }
            classText.append(' ').append(fieldName).append('\n');
        }
        classText.append('}');
        return GroovyPsiElementFactory.getInstance(project).createTypeDefinition(classText.toString());
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new MyPredicate();
    }
}

class MyPredicate implements PsiElementPredicate {
    @Override
    public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrListOrMap)) {
            return false;
        }
        GrListOrMap map = (GrListOrMap) element;
        GrNamedArgument[] namedArguments = map.getNamedArguments();
        GrExpression[] initializers = map.getInitializers();
        if (initializers.length != 0) {
            return false;
        }

        for (GrNamedArgument argument : namedArguments) {
            GrArgumentLabel label = argument.getLabel();
            GrExpression expression = argument.getExpression();
            if (label == null || expression == null) {
                return false;
            }
            if (label.getName() == null) {
                return false;
            }
        }
        return true;
    }
}
