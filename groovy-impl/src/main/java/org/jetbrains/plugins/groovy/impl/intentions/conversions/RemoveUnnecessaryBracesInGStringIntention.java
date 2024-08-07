/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.impl.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

/**
 * @author Maxim.Medvedev
 */
public class RemoveUnnecessaryBracesInGStringIntention extends Intention {
  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    GrStringUtil.removeUnnecessaryBracesInGString((GrString)element);
  }

  public static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      return isIntentionAvailable(element);
    }

    public static boolean isIntentionAvailable(PsiElement element) {
      if (!(element instanceof GrString)) return false;

      if (ErrorUtil.containsError(element)) return false;

      for (GrStringInjection child : ((GrString)element).getInjections()) {
        if (GrStringUtil.checkGStringInjectionForUnnecessaryBraces(child)) return true;
      }
      return false;
    }
  }
}


