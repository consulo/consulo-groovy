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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.java.language.impl.psi.scope.NameHint;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;

import jakarta.annotation.Nonnull;

/**
 * Checks names of processed element because our Groovy processors don't do it
 *
 * @author Max Medvedev
 */
public class JavaResolverProcessor extends DelegatingScopeProcessor {
  private final NameHint myHint;

  public JavaResolverProcessor(PsiScopeProcessor delegate) {
    super(delegate);
    myHint = delegate.getHint(NameHint.KEY);
  }

  @Override
  public boolean execute(@Nonnull PsiElement element, ResolveState state) {
    if (myHint != null && element instanceof PsiNamedElement) {
      final String expectedName = myHint.getName(state);
      final String elementName = ((PsiNamedElement)element).getName();
      if (expectedName != null && !expectedName.equals(elementName)) {
        return true;
      }
    }


    return super.execute(element, state);
  }

}
