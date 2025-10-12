/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.annotator.inspections;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiModifierList;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Dmitry.Krasilschikov
 * @since 2009-04-29
 */
public class GroovySingletonAnnotationInspection extends BaseInspection {
    public static final String SINGLETON = GroovyCommonClassNames.GROOVY_LANG_SINGLETON;

    protected BaseInspectionVisitor buildVisitor() {
        return new Visitor();
    }

    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return ANNOTATIONS_ISSUES;
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Check '@Singleton' annotation conventions");
    }

    private static class Visitor extends BaseInspectionVisitor {
        public void visitAnnotation(GrAnnotation annotation) {
            super.visitAnnotation(annotation);

            PsiElement parent = annotation.getParent().getParent();
            if (parent == null || !(parent instanceof GrTypeDefinition)) {
                return;
            }

            if (SINGLETON.equals(annotation.getQualifiedName())) {
                GrTypeDefinition typeDefinition = (GrTypeDefinition) parent;

                PsiMethod[] methods = typeDefinition.getMethods();
                for (PsiMethod method : methods) {
                    if (method.isConstructor()) {
                        PsiModifierList modifierList = method.getModifierList();

                        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
                            registerClassError(typeDefinition);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected String buildErrorString(Object... args) {
        return GroovyLocalize.singletonClassShouldHavePrivateConstructor().get();
    }
}
