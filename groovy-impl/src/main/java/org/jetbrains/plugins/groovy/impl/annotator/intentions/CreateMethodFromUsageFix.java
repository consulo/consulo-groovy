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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.impl.codeInsight.generation.OverrideImplementUtil;
import com.intellij.java.impl.codeInsight.generation.PsiGenerationInfo;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GrStaticChecker;
import org.jetbrains.plugins.groovy.impl.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class CreateMethodFromUsageFix extends GrCreateFromUsageBaseFix implements IntentionAction {
    public CreateMethodFromUsageFix(@Nonnull GrReferenceExpression refExpression) {
        super(refExpression);
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public String getText() {
        return GroovyLocalize.createMethodFromUsage(getMethodName()).get();
    }

    @Override
    @RequiredReadAction
    protected void invokeImpl(Project project, @Nonnull PsiClass targetClass) {
        final JVMElementFactory factory =
            JVMElementFactoryProvider.forLanguage(targetClass.getProject(), targetClass.getLanguage());
        assert factory != null;
        PsiMethod method = factory.createMethod(getMethodName(), PsiType.VOID);

        final GrReferenceExpression ref = getRefExpr();
        if (GrStaticChecker.isInStaticContext(ref, targetClass)) {
            method.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
        }

        PsiType[] argTypes = getArgumentTypes();
        assert argTypes != null;

        ChooseTypeExpression[] paramTypesExpressions = setupParams(method, argTypes, factory);

        TypeConstraint[] constraints = getReturnTypeConstraints();

        final PsiGenerationInfo<PsiMethod> info = OverrideImplementUtil.createGenerationInfo(method);
        info.insert(targetClass, findInsertionAnchor(info, targetClass), false);
        method = info.getPsiMember();

        if (shouldBeAbstract(targetClass)) {
            method.getBody().delete();
            if (!targetClass.isInterface()) {
                method.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
            }
        }

        final PsiElement context = PsiTreeUtil.getParentOfType(ref, PsiClass.class, PsiMethod.class, PsiFile.class);
        IntentionUtils.createTemplateForMethod(
            argTypes,
            paramTypesExpressions,
            method,
            targetClass,
            constraints,
            false,
            context
        );
    }

    @Nonnull
    @RequiredReadAction
    protected TypeConstraint[] getReturnTypeConstraints() {
        return GroovyExpectedTypesProvider.calculateTypeConstraints((GrExpression)getRefExpr().getParent());
    }

    @RequiredReadAction
    protected PsiType[] getArgumentTypes() {
        return PsiUtil.getArgumentTypes(getRefExpr(), false);
    }

    @Nonnull
    @RequiredReadAction
    protected String getMethodName() {
        return getRefExpr().getReferenceName();
    }

    protected boolean shouldBeAbstract(PsiClass aClass) {
        return aClass.isInterface() && !GrTraitUtil.isTrait(aClass);
    }

    @Nullable
    @RequiredReadAction
    private PsiElement findInsertionAnchor(PsiGenerationInfo<PsiMethod> info, PsiClass targetClass) {
        PsiElement parent = targetClass instanceof GroovyScriptClass scriptClass ? scriptClass.getContainingFile() : targetClass;
        return PsiTreeUtil.isAncestor(parent, getRefExpr(), false) ? info.findInsertionAnchor(targetClass, getRefExpr()) : null;
    }

    @Nonnull
    @RequiredReadAction
    private ChooseTypeExpression[] setupParams(@Nonnull PsiMethod method, @Nonnull PsiType[] argTypes, @Nonnull JVMElementFactory factory) {
        final PsiParameterList parameterList = method.getParameterList();

        ChooseTypeExpression[] paramTypesExpressions = new ChooseTypeExpression[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            PsiType argType = TypesUtil.unboxPrimitiveTypeWrapper(argTypes[i]);
            if (argType == null || argType == PsiType.NULL) {
                argType = TypesUtil.getJavaLangObject(getRefExpr());
            }
            final PsiParameter p = factory.createParameter("o", argType);
            parameterList.add(p);
            TypeConstraint[] constraints = {SupertypeConstraint.create(argType)};
            boolean isGroovy = method.getLanguage() == GroovyLanguage.INSTANCE;
            paramTypesExpressions[i] = new ChooseTypeExpression(constraints, method.getManager(), method.getResolveScope(), isGroovy);
        }
        return paramTypesExpressions;
    }
}
