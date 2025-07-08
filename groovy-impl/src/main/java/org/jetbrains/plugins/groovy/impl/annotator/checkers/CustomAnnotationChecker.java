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
package org.jetbrains.plugins.groovy.impl.annotator.checkers;

import com.intellij.java.language.impl.codeInsight.daemon.JavaErrorBundle;
import com.intellij.java.language.psi.*;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CustomAnnotationChecker {
    public static final ExtensionPointName<CustomAnnotationChecker> EP_NAME = ExtensionPointName.create(CustomAnnotationChecker.class);

    public boolean checkArgumentList(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
        return false;
    }

    public boolean checkApplicability(@Nonnull AnnotationHolder holder, @Nonnull GrAnnotation annotation) {
        return false;
    }

    @Nullable
    public static String isAnnotationApplicable(@Nonnull GrAnnotation annotation, @Nullable PsiAnnotationOwner owner) {
        if (!(owner instanceof PsiElement)) {
            return null;
        }
        PsiElement ownerToUse = owner instanceof PsiModifierList modifierList ? modifierList.getParent() : (PsiElement) owner;
        PsiAnnotation.TargetType[] elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
        if (elementTypeFields.length != 0 && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, elementTypeFields)) {
            GrCodeReferenceElement ref = annotation.getClassReference();
            return JavaErrorBundle.message("annotation.not.applicable", ref.getText(), elementTypeFields[0].getPresentableText());
        }

        return null;
    }

    public static Pair<PsiElement, String> checkAnnotationArguments(
        @Nonnull PsiClass annotation,
        @Nonnull GrAnnotationNameValuePair[] attributes,
        boolean checkMissedAttributes
    ) {
        Set<String> usedAttrs = new HashSet<>();

        if (attributes.length > 0) {
            PsiElement identifier = attributes[0].getNameIdentifierGroovy();
            if (attributes.length == 1 && identifier == null) {
                Pair.NonNull<PsiElement, String> r =
                    checkAnnotationValue(annotation, attributes[0], "value", usedAttrs, attributes[0].getValue());
                if (r != null) {
                    return r;
                }
            }
            else {
                for (GrAnnotationNameValuePair attribute : attributes) {
                    String name = attribute.getName();
                    if (name != null) {
                        PsiElement toHighlight = attribute.getNameIdentifierGroovy();
                        assert toHighlight != null;
                        Pair.NonNull<PsiElement, String> r =
                            checkAnnotationValue(annotation, toHighlight, name, usedAttrs, attribute.getValue());
                        if (r != null) {
                            return r;
                        }
                    }
                }
            }
        }

        List<String> missedAttrs = new ArrayList<>();
        PsiMethod[] methods = annotation.getMethods();
        for (PsiMethod method : methods) {
            String name = method.getName();
            if (usedAttrs.contains(name) ||
                method instanceof PsiAnnotationMethod annotationMethod && annotationMethod.getDefaultValue() != null) {
                continue;
            }
            missedAttrs.add(name);
        }

        if (checkMissedAttributes && !missedAttrs.isEmpty()) {
            return Pair.create(null, GroovyLocalize.missedAttributes(StringUtil.join(missedAttrs, ", ")).get());
        }
        return null;
    }

    private static Pair.NonNull<PsiElement, String> checkAnnotationValue(
        @Nonnull PsiClass annotation,
        @Nonnull PsiElement identifierToHighlight,
        @Nonnull String name,
        @Nonnull Set<? super String> usedAttrs,
        @Nullable GrAnnotationMemberValue value
    ) {
        if (!usedAttrs.add(name)) {
            return Pair.createNonNull(identifierToHighlight, GroovyLocalize.duplicateAttribute().get());
        }

        PsiMethod[] methods = annotation.findMethodsByName(name, false);
        if (methods.length == 0) {
            return Pair.createNonNull(
                identifierToHighlight,
                GroovyLocalize.atInterface0DoesNotContainAttribute(annotation.getQualifiedName(), name).get()
            );
        }
        PsiMethod method = methods[0];
        PsiType ltype = method.getReturnType();
        if (ltype != null && value != null) {
            return checkAnnotationValueByType(value, ltype, true);
        }
        return null;
    }

    public static Pair.NonNull<PsiElement, String> checkAnnotationValueByType(
        @Nonnull GrAnnotationMemberValue value,
        @Nullable PsiType ltype,
        boolean skipArrays
    ) {
        GlobalSearchScope resolveScope = value.getResolveScope();
        PsiManager manager = value.getManager();

        if (value instanceof GrExpression) {
            PsiType rtype;
            if (value instanceof GrFunctionalExpression) {
                rtype = PsiType.getJavaLangClass(manager, resolveScope);
            }
            else {
                rtype = ((GrExpression) value).getType();
            }

            if (rtype != null && !isAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
                return Pair.createNonNull(
                    value,
                    GroovyLocalize.cannotAssign(rtype.getPresentableText(), ltype.getPresentableText()).get()
                );
            }
        }

        else if (value instanceof GrAnnotation annotation) {
            if (annotation.getClassReference().resolve() instanceof PsiClass resolvedClass) {
                PsiClassType rtype =
                    JavaPsiFacade.getElementFactory(value.getProject()).createType(resolvedClass, PsiSubstitutor.EMPTY);
                if (!isAnnoTypeAssignable(ltype, rtype, value, skipArrays)) {
                    return Pair.createNonNull(
                        value,
                        GroovyLocalize.cannotAssign(rtype.getPresentableText(), ltype.getPresentableText()).get()
                    );
                }
            }
        }

        else if (value instanceof GrAnnotationArrayInitializer annotationArrayInitializer) {
            if (ltype instanceof PsiArrayType arrayType) {
                PsiType componentType = arrayType.getComponentType();
                GrAnnotationMemberValue[] initializers = annotationArrayInitializer.getInitializers();
                for (GrAnnotationMemberValue initializer : initializers) {
                    Pair.NonNull<PsiElement, String> r = checkAnnotationValueByType(initializer, componentType, false);
                    if (r != null) {
                        return r;
                    }
                }
            }
            else {
                PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer(annotationArrayInitializer);
                if (!isAnnoTypeAssignable(ltype, rtype, annotationArrayInitializer, skipArrays)) {
                    return Pair.createNonNull(
                        value,
                        GroovyLocalize.cannotAssign(rtype.getPresentableText(), ltype.getPresentableText()).get()
                    );
                }
            }
        }
        return null;
    }

    private static boolean isAnnoTypeAssignable(
        @Nullable PsiType type,
        @Nullable PsiType rtype,
        @Nonnull GroovyPsiElement context,
        boolean skipArrays
    ) {
        rtype = TypesUtil.unboxPrimitiveTypeWrapper(rtype);
        if (TypesUtil.isAssignableByMethodCallConversion(type, rtype, context)) {
            return true;
        }

        if (!(type instanceof PsiArrayType arrayType && skipArrays)) {
            return false;
        }

        PsiType componentType = arrayType.getComponentType();
        return isAnnoTypeAssignable(componentType, rtype, context, skipArrays);
    }
}
