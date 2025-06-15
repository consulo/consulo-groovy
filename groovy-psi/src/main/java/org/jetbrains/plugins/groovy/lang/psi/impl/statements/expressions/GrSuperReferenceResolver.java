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

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 * @since 2014-06-15
 */
public class GrSuperReferenceResolver {
    /**
     * @return null if ref is not 'super' reference
     */
    @Nullable
    public static GroovyResolveResult[] resolveSuperExpression(@Nonnull GrReferenceExpression ref) {
        GrExpression qualifier = ref.getQualifier();

        if (qualifier == null) {
            final PsiElement parent = ref.getParent();
            if (parent instanceof GrConstructorInvocation constructorInvocation) {
                return constructorInvocation.multiResolveGroovy(false);
            }
            PsiClass aClass = PsiUtil.getContextClass(ref);
            if (aClass != null) {
                return getSuperClass(aClass);
            }
        }
        else if (qualifier instanceof GrReferenceExpression qualifierRefExpr) {
            GroovyResolveResult result = qualifierRefExpr.advancedResolve();
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiClass superClass) {
                GrTypeDefinition scopeClass = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, true);
                if (scopeClass != null && GrTraitUtil.isTrait(superClass) && scopeClass.isInheritor(superClass, false)) {
                    PsiSubstitutor superClassSubstitutor =
                        TypeConversionUtil.getSuperClassSubstitutor(superClass, scopeClass, PsiSubstitutor.EMPTY);
                    return new GroovyResolveResultImpl[]{new GroovyResolveResultImpl(
                        superClass,
                        null,
                        null,
                        superClassSubstitutor,
                        true,
                        true
                    )};
                }

                if (PsiUtil.hasEnclosingInstanceInScope(superClass, ref, false)) {
                    return getSuperClass(superClass);
                }
            }
        }

        return null;
    }

    @Nonnull
    private static GroovyResolveResult[] getSuperClass(@Nonnull PsiClass aClass) {
        PsiClass superClass = aClass.getSuperClass();
        if (superClass != null) {
            PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
            return new GroovyResolveResultImpl[]{new GroovyResolveResultImpl(superClass, null, null, superClassSubstitutor, true, true)};
        }
        else {
            return GroovyResolveResult.EMPTY_ARRAY; //no super class, but the reference is definitely super-reference
        }
    }
}
