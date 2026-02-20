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

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.util.ProcessingContext;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import jakarta.annotation.Nonnull;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ClassUtil {
    private static final LightCacheKey<Map<String, PsiClass>> PARENT_CACHE_KEY = LightCacheKey.create();

    public static Map<String, PsiClass> getSuperClassesWithCache(@Nonnull PsiClass aClass) {
        Map<String, PsiClass> superClassNames = PARENT_CACHE_KEY.getCachedValue(aClass);
        if (superClassNames == null) {
            Set<PsiClass> superClasses = new HashSet<>();
            superClasses.add(aClass);
            InheritanceUtil.getSuperClasses(aClass, superClasses, true);

            superClassNames = new LinkedHashMap<>();
            for (PsiClass superClass : superClasses) {
                superClassNames.put(superClass.getQualifiedName(), superClass);
            }

            superClassNames = PARENT_CACHE_KEY.putCachedValue(aClass, superClassNames);
        }

        return superClassNames;
    }

    @Nonnull
    public static PsiType findPsiType(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
        String typeText = descriptor.getTypeText();
        String key = getClassKey(typeText);
        Object cached = ctx.get(key);
        if (cached instanceof PsiType type) {
            return type;
        }

        PsiType found =
            JavaPsiFacade.getElementFactory(descriptor.getProject()).createTypeFromText(typeText, descriptor.getPlaceFile());
        ctx.put(key, found);
        return found;
    }

    public static String getClassKey(String fqName) {
        return "Class: " + fqName;
    }
}
