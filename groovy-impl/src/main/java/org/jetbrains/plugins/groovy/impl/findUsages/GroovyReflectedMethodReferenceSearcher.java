/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearchExecutor;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiReference;
import consulo.project.util.query.QueryExecutorBase;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyReflectedMethodReferenceSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> implements MethodReferencesSearchExecutor {
  public GroovyReflectedMethodReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@Nonnull MethodReferencesSearch.SearchParameters queryParameters,
                           @Nonnull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    if (method instanceof GrMethod) {
      for (GrReflectedMethod reflectedMethod : ((GrMethod)method).getReflectedMethods()) {
        MethodReferencesSearch.search(reflectedMethod, queryParameters.getScope(), true).forEach(consumer);
      }
    }
  }
}
