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
package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.indexing.impl.MethodImplementationsSearch;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.search.DefinitionsScopedSearch;
import consulo.language.psi.search.DefinitionsScopedSearchExecutor;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyImplementationSearch implements DefinitionsScopedSearchExecutor {
    @Override
    public boolean execute(
        @Nonnull DefinitionsScopedSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiElement> consumer
    ) {
        return Application.get().runReadAction((Supplier<Boolean>)() -> {
            PsiElement source = queryParameters.getElement();
            if (!source.isValid()) {
                return true;
            }

            if (source instanceof GrAccessorMethod accessorMethod) {
                GrField property = accessorMethod.getProperty();
                return consumer.test(property);
            }
            else {
                SearchScope searchScope = queryParameters.getScope();
                if (source instanceof GrMethod method) {
                    GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
                    for (GrReflectedMethod reflectedMethod : reflectedMethods) {
                        if (!MethodImplementationsSearch.processImplementations(reflectedMethod, consumer, searchScope)) {
                            return false;
                        }
                    }
                }

                else if (source instanceof GrField field) {
                    for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors(field)) {
                        if (!MethodImplementationsSearch.processImplementations(method, consumer, searchScope)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        });
    }
}
