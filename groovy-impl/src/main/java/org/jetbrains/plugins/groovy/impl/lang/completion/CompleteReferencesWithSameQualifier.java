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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import consulo.application.util.matcher.PrefixMatcher;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by Max Medvedev on 25/04/14
 */
public class CompleteReferencesWithSameQualifier {
  private final GrReferenceExpression myRefExpr;
  private final PrefixMatcher myMatcher;
  private final GrExpression myQualifier;

  private CompleteReferencesWithSameQualifier(@Nonnull GrReferenceExpression refExpr,
                                              @Nonnull PrefixMatcher matcher,
                                              @Nullable GrExpression qualifier) {
    myRefExpr = refExpr;
    myMatcher = matcher;
    myQualifier = qualifier;
  }

  @Nonnull
  public static Set<String> getVariantsWithSameQualifier(@Nonnull GrReferenceExpression refExpr,
                                                         @Nonnull PrefixMatcher matcher,
                                                         @Nullable GrExpression qualifier) {
    return new CompleteReferencesWithSameQualifier(refExpr, matcher, qualifier).getVariantsWithSameQualifierImpl();
  }

  private Set<String> getVariantsWithSameQualifierImpl() {
    if (myQualifier != null && myQualifier.getType() != null) return Collections.emptySet();

    final PsiElement scope = PsiTreeUtil.getParentOfType(myRefExpr, GrMember.class, PsiFile.class);
    Set<String> result = new LinkedHashSet<>();
    if (scope != null) {
      addVariantsWithSameQualifier(scope, result);
    }
    return result;
  }

  private void addVariantsWithSameQualifier(@Nonnull PsiElement element, @Nonnull Set<String> result) {
    if (element instanceof GrReferenceExpression && element != myRefExpr && !PsiUtil.isLValue((GroovyPsiElement)element)) {
      final GrReferenceExpression refExpr = (GrReferenceExpression)element;
      final String refName = refExpr.getReferenceName();
      if (refName != null && !result.contains(refName) && myMatcher.prefixMatches(refName)) {
        final GrExpression hisQualifier = refExpr.getQualifierExpression();
        if (hisQualifier != null && myQualifier != null) {
          if (PsiEquivalenceUtil.areElementsEquivalent(hisQualifier, myQualifier)) {
            if (refExpr.resolve() == null) {
              result.add(refName);
            }
          }
        }
        else if (hisQualifier == null && myQualifier == null) {
          if (refExpr.resolve() == null) {
            result.add(refName);
          }
        }
      }
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      addVariantsWithSameQualifier(child, result);
    }
  }
}
