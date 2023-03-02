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

import javax.annotation.Nonnull;

import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyMapCompletionUtil;
import org.jetbrains.plugins.groovy.impl.extensions.GroovyMapContentProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import consulo.language.util.ProcessingContext;
import consulo.language.editor.completion.CompletionProvider;

/**
 * @author Sergey Evdokimov
 */
class MapKeysCompletionProvider implements CompletionProvider
{
  public static void register(CompletionContributor contributor) {
    MapKeysCompletionProvider provider = new MapKeysCompletionProvider();

    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class)), provider);
  }

  @Override
  public void addCompletions(@Nonnull CompletionParameters parameters,
                                ProcessingContext context,
                                @Nonnull CompletionResultSet result) {
    PsiElement element = parameters.getPosition();
    GrReferenceExpression expression = (GrReferenceExpression)element.getParent();

    GrExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null) return;

    PsiType mapType = qualifierExpression.getType();

    if (!GroovyPsiManager.isInheritorCached(mapType, CommonClassNames.JAVA_UTIL_MAP)) {
      return;
    }

    PsiElement resolve = null;

    if (qualifierExpression instanceof GrMethodCall) {
      resolve = ((GrMethodCall)qualifierExpression).resolveMethod();
    }
    else if (qualifierExpression instanceof GrReferenceExpression) {
      resolve = ((GrReferenceExpression)qualifierExpression).resolve();
    }

    for (GroovyMapContentProvider provider : GroovyMapContentProvider.EP_NAME.getExtensions()) {
      GroovyMapCompletionUtil.addKeyVariants(provider, qualifierExpression, resolve, result);
    }

    if (mapType instanceof GrMapType) {
      for (String key : ((GrMapType)mapType).getStringKeys()) {
        LookupElement lookup = LookupElementBuilder.create(key);
        lookup = PrioritizedLookupElement.withPriority(lookup, 1);
        result.addElement(lookup);
      }
    }
  }
}
