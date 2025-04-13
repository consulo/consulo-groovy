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

package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.indexing.search.searches.DirectClassInheritorsSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearchExecutor;
import com.intellij.java.language.impl.psi.impl.light.LightMemberReference;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.ReadActionProcessor;
import consulo.content.scope.SearchScope;
import consulo.document.util.TextRange;
import consulo.language.impl.psi.PsiAnchor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.SearchRequestCollector;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyConstructorUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters>
    implements MethodReferencesSearchExecutor {
    public GroovyConstructorUsagesSearcher() {
        super(true);
    }

    @Override
    @RequiredReadAction
    public void processQuery(@Nonnull MethodReferencesSearch.SearchParameters p, @Nonnull Predicate<? super PsiReference> consumer) {
        processConstructorUsages(p.getMethod(), p.getScope(), consumer, p.getOptimizer(), true, !p.isStrictSignatureSearch());
    }

    public static final Key<Set<PsiClass>> LITERALLY_CONSTRUCTED_CLASSES = Key.create("LITERALLY_CONSTRUCTED_CLASSES");

    @RequiredReadAction
    static void processConstructorUsages(
        PsiMethod constructor,
        SearchScope searchScope,
        Predicate<? super PsiReference> consumer,
        SearchRequestCollector collector,
        boolean searchGppCalls,
        boolean includeOverloads
    ) {
        if (!constructor.isConstructor()) {
            return;
        }

        PsiClass clazz = constructor.getContainingClass();
        if (clazz == null) {
            return;
        }

        SearchScope onlyGroovy = GroovyScopeUtil.restrictScopeToGroovyFiles(searchScope, GroovyScopeUtil.getEffectiveScope(constructor));
        Set<PsiClass> processed = collector.getSearchSession().getUserData(LITERALLY_CONSTRUCTED_CLASSES);
        if (processed == null) {
            collector.getSearchSession().putUserData(LITERALLY_CONSTRUCTED_CLASSES, processed = ContainerUtil.newConcurrentSet());
        }
        if (!processed.add(clazz)) {
            return;
        }

        if (clazz.isEnum() && clazz instanceof GroovyPsiElement) {
            for (PsiField field : clazz.getFields()) {
                if (field instanceof GrEnumConstant) {
                    PsiReference ref = field.getReference();
                    if (ref != null && ref.isReferenceTo(constructor) && !consumer.test(ref)) {
                        return;
                    }
                }
            }
        }

        LiteralConstructorSearcher literalProcessor = new LiteralConstructorSearcher(constructor, consumer, includeOverloads);

        Predicate<GrNewExpression> newExpressionProcessor = grNewExpression -> {
            PsiMethod resolvedConstructor = grNewExpression.resolveMethod();
            return !(includeOverloads || constructor.getManager().areElementsEquivalent(resolvedConstructor, constructor))
                || consumer.test(grNewExpression.getReferenceElement());
        };

        processGroovyClassUsages(clazz, searchScope, collector, searchGppCalls, newExpressionProcessor, literalProcessor);

        //this()
        if (clazz instanceof GrTypeDefinition) {
            if (!processConstructors(constructor, consumer, clazz, true)) {
                return;
            }
        }
        //super()
        DirectClassInheritorsSearch.search(clazz, onlyGroovy).forEach(new ReadActionProcessor<>() {
            @Override
            @RequiredReadAction
            public boolean processInReadAction(PsiClass inheritor) {
                return !(inheritor instanceof GrTypeDefinition)
                    || processConstructors(constructor, consumer, inheritor, false);
            }
        });
    }

    @RequiredReadAction
    public static void processGroovyClassUsages(
        PsiClass clazz,
        SearchScope scope,
        SearchRequestCollector collector,
        boolean searchGppCalls,
        Predicate<GrNewExpression> newExpressionProcessor,
        LiteralConstructorSearcher literalProcessor
    ) {
        Set<PsiAnchor> processedMethods = ContainerUtil.newConcurrentSet();

        ReferencesSearch.searchOptimized(clazz, scope, true, collector, true, (ref, collector1) -> {
            PsiElement element = ref.getElement();

            if (element instanceof GrCodeReferenceElement) {
                if (!processGroovyConstructorUsages(
                    (GrCodeReferenceElement)element,
                    !searchGppCalls,
                    newExpressionProcessor,
                    literalProcessor
                )) {
                    return false;
                }
            }

            return true;
        });
    }

    @Nullable
    private static PsiMethod getMethodToSearchForCallsWithLiteralArguments(PsiElement element, PsiClass targetClass) {
        PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
        if (parameter != null) {
            PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class);
            if (method != null) {
                PsiParameter[] parameters = method.getParameterList().getParameters();
                int idx = Arrays.asList(parameters).indexOf(parameter);
                if (idx >= 0) {
                    PsiType parameterType = parameter.getType();
                    if (parameterType instanceof PsiArrayType arrayType && idx == parameters.length - 1) {
                        parameterType = arrayType.getComponentType();
                    }
                    if (parameterType instanceof PsiClassType classType
                        && method.getManager().areElementsEquivalent(targetClass, classType.resolve())) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static boolean processGroovyConstructorUsages(
        GrCodeReferenceElement element,
        boolean usualCallsOnly,
        Predicate<GrNewExpression> newExpressionProcessor,
        LiteralConstructorSearcher literalProcessor
    ) {
        PsiElement parent = element.getParent();

        if (parent instanceof GrAnonymousClassDefinition) {
            parent = parent.getParent();
        }
        if (parent instanceof GrNewExpression newExpr) {
            return newExpressionProcessor.test(newExpr);
        }

        if (usualCallsOnly) {
            return true;
        }

        if (parent instanceof GrTypeElement typeElement) {
            PsiElement grandpa = typeElement.getParent();
            if (grandpa instanceof GrVariableDeclaration varDecl) {
                GrVariable[] vars = varDecl.getVariables();
                if (vars.length == 1) {
                    GrVariable variable = vars[0];
                    if (!checkLiteralInstantiation(variable.getInitializerGroovy(), literalProcessor)) {
                        return false;
                    }
                }
            }
            else if (grandpa instanceof GrMethod method) {
                if (typeElement == method.getReturnTypeElementGroovy()) {
                    ControlFlowUtils.visitAllExitPoints(
                        method.getBlock(),
                        (instruction, returnValue) -> checkLiteralInstantiation(returnValue, literalProcessor)
                    );
                }
            }
            else if (grandpa instanceof GrTypeCastExpression cast) {
                if (cast.getCastTypeElement() == typeElement
                    && !checkLiteralInstantiation(cast.getOperand(), literalProcessor)) {
                    return false;
                }
            }
            else if (grandpa instanceof GrSafeCastExpression cast) {
                if (cast.getCastTypeElement() == typeElement
                    && !checkLiteralInstantiation(cast.getOperand(), literalProcessor)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean checkLiteralInstantiation(GrExpression expression, LiteralConstructorSearcher literalProcessor) {
        return !(expression instanceof GrListOrMap listOrMap && !literalProcessor.processLiteral(listOrMap));
    }

    private static boolean processConstructors(
        PsiMethod searchedConstructor,
        Predicate<? super PsiReference> consumer,
        PsiClass clazz,
        boolean processThisRefs
    ) {
        PsiMethod[] constructors = clazz.getConstructors();
        if (constructors.length == 0) {
            processImplicitConstructorCall(clazz, consumer, searchedConstructor);
        }
        for (PsiMethod constructor : constructors) {
            GrOpenBlock block = ((GrMethod)constructor).getBlock();
            if (block != null) {
                GrStatement[] statements = block.getStatements();
                if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation invocation) {
                    if (invocation.isThisCall() == processThisRefs
                        && invocation.getManager().areElementsEquivalent(invocation.resolveMethod(), searchedConstructor)
                        && !consumer.test(invocation.getInvokedExpression())) {
                        return false;
                    }
                }
                else {
                    processImplicitConstructorCall(constructor, consumer, searchedConstructor);
                }
            }
        }
        return true;
    }

    private static void processImplicitConstructorCall(
        PsiMember usage,
        Predicate<? super PsiReference> processor,
        PsiMethod constructor
    ) {
        if (constructor instanceof GrMethod) {
            GrParameter[] grParameters = (GrParameter[])constructor.getParameterList().getParameters();
            if (grParameters.length > 0 && !grParameters[0].isOptional()) {
                return;
            }
        }
        else if (constructor.getParameterList().getParameters().length > 0) {
            return;
        }

        PsiManager manager = constructor.getManager();
        if (manager.areElementsEquivalent(usage, constructor)
            || manager.areElementsEquivalent(constructor.getContainingClass(), usage.getContainingClass())) {
            return;
        }

        processor.test(new LightMemberReference(manager, usage, PsiSubstitutor.EMPTY) {
            @Override
            public PsiElement getElement() {
                return usage;
            }

            @Nonnull
            @Override
            @RequiredReadAction
            public TextRange getRangeInElement() {
                if (usage instanceof PsiClass psiClass) {
                    PsiIdentifier identifier = psiClass.getNameIdentifier();
                    if (identifier != null) {
                        return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
                    }
                }
                else if (usage instanceof PsiMethod method) {
                    PsiIdentifier identifier = method.getNameIdentifier();
                    if (identifier != null) {
                        return TextRange.from(identifier.getStartOffsetInParent(), identifier.getTextLength());
                    }
                }
                return super.getRangeInElement();
            }
        });
    }
}
