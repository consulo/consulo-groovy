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

import com.intellij.java.indexing.impl.search.MethodTextOccurrenceProcessor;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.indexing.search.searches.MethodReferencesSearchExecutor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrLiteralMethodSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> implements MethodReferencesSearchExecutor {
  @Override
  public void processQuery(@Nonnull MethodReferencesSearch.SearchParameters p, @Nonnull Processor<? super PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    final String name = method.getName();
    if (StringUtil.isEmpty(name)) return;

    final boolean strictSignatureSearch = p.isStrictSignatureSearch();
    final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(name, false);

    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      accessScope = accessScope.union(method1.getUseScope());
    }

    final SearchScope restrictedByAccess = p.getScope().intersectWith(accessScope);

    final String textToSearch = findLongestWord(name);

    p.getOptimizer().searchWord(textToSearch, restrictedByAccess, UsageSearchContext.IN_STRINGS, true,
                                new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods));
  }

  @Nonnull
  private static String findLongestWord(@Nonnull String sequence) {
    final List<String> words = StringUtil.getWordsIn(sequence);
    if (words.isEmpty()) return sequence;

    String longest = words.get(0);
    for (String word : words) {
      if (word.length() > longest.length()) longest = word;
    }

    return longest;
  }

  public GrLiteralMethodSearcher() {
    super(true);
  }
}
