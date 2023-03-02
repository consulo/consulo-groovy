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
package org.jetbrains.plugins.groovy.impl.lang.completion.weighers;

import consulo.language.editor.completion.CompletionLocation;
import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.ResolveResult;
import javax.annotation.Nonnull;

import consulo.language.editor.completion.CompletionWeigher;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.ResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * @author Max Medvedev
 */
public class GrAccessorWeigher extends CompletionWeigher
{
  @Override
  public Integer weigh(@Nonnull LookupElement element, @Nonnull CompletionLocation location) {
    if (!(location.getCompletionParameters().getPosition().getContainingFile() instanceof GroovyFileBase)) {
      return null;
    }

    Object o = element.getObject();
    if (o instanceof ResolveResult) {
      o = ((ResolveResult)o).getElement();
    }

    if (o instanceof PsiMethod &&
        (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)o) || "setProperty".equals(((PsiMethod)o).getName()))) {
      return 1;
    }
    return 0;
  }
}
