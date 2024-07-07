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
package org.jetbrains.plugins.groovy.impl.findUsages;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.usage.Usage;
import consulo.usage.rule.ImportFilteringRule;
import consulo.usage.rule.PsiElementUsage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrImportFilteringRule implements ImportFilteringRule {
  @Override
  public boolean isVisible(@Nonnull Usage usage) {
    if (usage instanceof PsiElementUsage) {
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof GroovyFile) {
        // check whether the element is in the import list
        return PsiTreeUtil.getParentOfType(psiElement, GrImportStatement.class, true) == null;
      }
    }
    return true;
  }
}
