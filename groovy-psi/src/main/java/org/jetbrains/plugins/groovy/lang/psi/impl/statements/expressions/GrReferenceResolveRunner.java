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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.application.progress.ProgressManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.ResolveState;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTraitType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ResolverProcessorImpl;

import java.util.List;
import java.util.ListIterator;

/**
 * @author Max Medvedev
 */
public class GrReferenceResolveRunner {
    private final GrReferenceExpression place;
    private ResolverProcessorImpl processor;

    public GrReferenceResolveRunner(@Nonnull GrReferenceExpression _place) {
        place = _place;
    }

    public boolean resolveImpl(@Nonnull ResolverProcessorImpl _processor) {
        processor = _processor;
        try {
            boolean result = doResolve();
            ProgressManager.checkCanceled();
            return result;
        }
        finally {
            processor = null;
        }
    }

    private boolean doResolve() {
        GrExpression qualifier = place.getQualifier();
        if (qualifier == null) {
            if (!ResolveUtil.treeWalkUp(place, processor, true)) {
                return false;
            }
            if (!processor.hasCandidates()) {
                GrExpression runtimeQualifier = PsiImplUtil.getRuntimeQualifier(place);
                if (runtimeQualifier != null && !processQualifier(runtimeQualifier)) {
                    return false;
                }
            }
        }
        else if (place.getDotTokenType() == GroovyTokenTypes.mSPREAD_DOT) {
            final PsiType qtype = qualifier.getType();
            final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qtype, place);
            if (componentType != null) {
                final ResolveState state = ResolveState.initial()
                    .put(ClassHint.RESOLVE_CONTEXT, qualifier)
                    .put(SpreadState.SPREAD_STATE, SpreadState.create(qtype, null));
                if (!processQualifierType(componentType, state)) {
                    return false;
                }
            }
        }
        else {
            if (ResolveUtil.isClassReference(place)) {
                return true;
            }
            if (!processQualifier(qualifier) || !processJavaLangClass(qualifier)) {
                return false;
            }
        }
        return true;
    }

    private boolean processJavaLangClass(@Nonnull GrExpression qualifier) {
        if (qualifier instanceof GrReferenceExpression refExpr) {
            //optimization: only 'class' or 'this' in static context can be an alias of java.lang.Class
            if (!("class".equals(refExpr.getReferenceName()) || PsiUtil.isThisReference(qualifier))) {
                return true;
            }

            if (qualifier.getType() instanceof PsiClassType classType) {
                final PsiClass psiClass = classType.resolve();
                if (psiClass == null || !CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
                    return true;
                }

                final PsiType[] params = classType.getParameters();
                return params.length != 1
                    || processQualifierType(params[0], ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier));
            }
        }
        return true;
    }

    private boolean processQualifier(@Nonnull GrExpression qualifier) {
        PsiType qualifierType = qualifier.getType();
        ResolveState state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, qualifier);
        if (qualifierType == null || qualifierType == PsiType.VOID) {
            if (qualifier instanceof GrReferenceExpression refExpr) {
                PsiElement resolved = refExpr.resolve();
                if (resolved != null && !resolved.processDeclarations(processor, state, null, place)) {
                    return false;
                }
                if (!(resolved instanceof PsiJavaPackage)) {
                    PsiType objectQualifier = TypesUtil.getJavaLangObject(place);
                    if (!processQualifierType(objectQualifier, state)) {
                        return false;
                    }
                }
            }
        }
        else if (qualifierType instanceof PsiIntersectionType intersectionType) {
            for (PsiType conjunct : intersectionType.getConjuncts()) {
                if (!processQualifierType(conjunct, state)) {
                    return false;
                }
            }
        }
        else if (!processQualifierType(qualifierType, state)) {
            return false;
        }
        else if (qualifier instanceof GrReferenceExpression refExpr && !PsiUtil.isSuperReference(qualifier)
            && !PsiUtil.isInstanceThisRef(qualifier) && refExpr.resolve() instanceof PsiClass
            && !processJavaLangClass(qualifierType, state)) {
            return false;
        }
        return true;
    }

    private boolean processJavaLangClass(@Nonnull PsiType qualifierType, @Nonnull ResolveState state) {
        //omitted .class
        PsiClass javaLangClass = PsiUtil.getJavaLangClass(place, place.getResolveScope());
        if (javaLangClass == null) {
            return true;
        }

        PsiTypeParameter[] typeParameters = javaLangClass.getTypeParameters();
        PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
        if (substitutor == null) {
            substitutor = PsiSubstitutor.EMPTY;
        }
        if (typeParameters.length == 1) {
            substitutor = substitutor.put(typeParameters[0], qualifierType);
            state = state.put(PsiSubstitutor.KEY, substitutor);
        }
        if (!javaLangClass.processDeclarations(processor, state, null, place)) {
            return false;
        }

        PsiType javaLangClassType = JavaPsiFacade.getElementFactory(place.getProject()).createType(javaLangClass, substitutor);

        return ResolveUtil.processNonCodeMembers(javaLangClassType, processor, place, state);
    }

    private boolean processQualifierType(@Nonnull PsiType originalQualifierType, @Nonnull ResolveState state) {
        PsiType qualifierType = originalQualifierType instanceof PsiDisjunctionType disjunctionType
            ? disjunctionType.getLeastUpperBound()
            : originalQualifierType;

        if (qualifierType instanceof PsiIntersectionType intersectionType) {
            for (PsiType conjunct : intersectionType.getConjuncts()) {
                if (!processQualifierType(conjunct, state)) {
                    return false;
                }
            }
            return true;
        }

        if (qualifierType instanceof GrTraitType traitType) {
            return processTraitType(traitType, state);
        }

        if (qualifierType instanceof PsiClassType qualifierClassType) {
            PsiClassType.ClassResolveResult qualifierResult = qualifierClassType.resolveGenerics();
            PsiClass qualifierClass = qualifierResult.getElement();
            if (qualifierClass != null) {
                if (!qualifierClass.processDeclarations(
                    processor,
                    state.put(PsiSubstitutor.KEY, qualifierResult.getSubstitutor()),
                    null,
                    place
                )) {
                    return false;
                }
            }
        }
        else if (qualifierType instanceof PsiArrayType qualifierArrayType) {
            final GroovyPsiManager gmanager = GroovyPsiManager.getInstance(place.getProject());
            final GrTypeDefinition arrayClass = gmanager.getArrayClass(qualifierArrayType.getComponentType());
            if (arrayClass != null && !arrayClass.processDeclarations(processor, state, null, place)) {
                return false;
            }
        }

        if (!(place.getParent() instanceof GrMethodCall)
            && InheritanceUtil.isInheritor(qualifierType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
            final PsiType componentType = ClosureParameterEnhancer.findTypeForIteration(qualifierType, place);
            if (componentType != null) {
                final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
                processQualifierType(componentType, state.put(SpreadState.SPREAD_STATE, SpreadState.create(qualifierType, spreadState)));
            }
        }

        return ResolveUtil.processCategoryMembers(place, processor, state)
            && ResolveUtil.processNonCodeMembers(qualifierType, processor, place, state);
    }

    private boolean processTraitType(@Nonnull GrTraitType traitType, @Nonnull ResolveState state) {
        GrTypeDefinition mockDefinition = traitType.getMockTypeDefinition();
        if (mockDefinition != null) {
            if (!mockDefinition.processDeclarations(processor, state, null, place)) {
                return false;
            }
        }
        else {
            PsiClassType exprType = traitType.getExprType();

            if (!processQualifierType(exprType, state)) {
                return false;
            }

            List<PsiClassType> traitTypes = traitType.getTraitTypes();
            for (ListIterator<PsiClassType> iterator = traitTypes.listIterator(); iterator.hasPrevious(); ) {
                PsiClassType type = iterator.previous();
                if (!processQualifierType(type, state)) {
                    return false;
                }
            }
        }

        return true;
    }
}
