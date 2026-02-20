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
package org.jetbrains.plugins.groovy.impl.lang.completion.weighers;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;

import jakarta.annotation.Nonnull;

/**
 * Prefers elements that were added by 'with' closure: foo.with{ caret }
 *
 * @author Max Medvedev
 */
@ExtensionImpl(id = "groovyWithWeigher", order = "after prefix")
public class GrWithWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(@Nonnull LookupElement element, @Nonnull CompletionLocation location) {
    PsiElement position = location.getCompletionParameters().getPosition();
    if (position.getLanguage() == GroovyLanguage.INSTANCE) return null;

    if (!(position.getParent() instanceof GrReferenceExpression)) return null;

    Object o = element.getObject();
    if (!(o instanceof GroovyResolveResult)) return 0;

    PsiElement resolveContext = ((GroovyResolveResult)o).getCurrentFileResolveContext();

    if (resolveContext == null) return 0;

    if (GdkMethodUtil.isInWithContext(resolveContext)) {
      return 1;
    }

    return 0;
  }
}
