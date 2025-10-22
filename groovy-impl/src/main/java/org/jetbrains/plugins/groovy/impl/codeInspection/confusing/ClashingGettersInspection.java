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
package org.jetbrains.plugins.groovy.impl.codeInspection.confusing;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClashingGettersInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return CONFUSING_CODE_CONSTRUCTS;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Clashing getters");
    }

    @Nullable
    @Override
    protected String buildErrorString(Object... args) {
        return GroovyInspectionLocalize.getter0ClashesWithGetter1(args[0], args[1]).get();
    }

    @Nonnull
    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
                super.visitTypeDefinition(typeDefinition);

                Map<String, PsiMethod> getters = new HashMap<>();
                for (PsiMethod method : typeDefinition.getMethods()) {
                    String methodName = method.getName();
                    if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) {
                        continue;
                    }

                    String propertyName = GroovyPropertyUtils.getPropertyNameByGetterName(methodName, true);

                    PsiMethod otherGetter = getters.get(propertyName);
                    if (otherGetter != null && !methodName.equals(otherGetter.getName())) {
                        Pair<PsiElement, String> description = getGetterDescription(method);
                        Pair<PsiElement, String> otherDescription = getGetterDescription(otherGetter);

                        if (description.first != null) {
                            registerError(description.first, description.second, otherDescription.second);
                        }
                        if (otherDescription.first != null) {
                            registerError(otherDescription.first, otherDescription.second, description.second);
                        }
                    }
                    else {
                        getters.put(propertyName, method);
                    }
                }
            }
        };
    }

    private static Pair<PsiElement, String> getGetterDescription(PsiMethod getter) {
        String name = getter.getName();
        if (getter instanceof GrGdkMethod) {
            return new Pair<>(null, "GDK method '" + name + "'");
        }
        else if (getter instanceof GrReflectedMethod reflectedGetter) {
            getter = reflectedGetter.getBaseMethod();
            String info = PsiFormatUtil.formatMethod(
                getter,
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME
            );
            return Pair.create(reflectedGetter.getNameIdentifierGroovy(), "method " + info);
        }
        else if (getter instanceof GrMethod getterMethod) {
            return Pair.create(getterMethod.getNameIdentifierGroovy(), "getter '" + name + "'");
        }
        else {
            String info = PsiFormatUtil.formatMethod(
                getter,
                PsiSubstitutor.EMPTY,
                PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME
            );
            return new Pair<>(null, "method " + info);
        }
    }
}
