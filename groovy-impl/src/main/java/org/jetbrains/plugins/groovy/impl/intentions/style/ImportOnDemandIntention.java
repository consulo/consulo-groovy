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
package org.jetbrains.plugins.groovy.impl.intentions.style;

import jakarta.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.impl.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

/**
 * @author Maxim.Medvedev
 */
public class ImportOnDemandIntention extends Intention {

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    if (!(element instanceof GrReferenceElement)) return;
    final GrReferenceElement ref = (GrReferenceElement)element;
    final PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiClass)) return;

    final String qname = ((PsiClass)resolved).getQualifiedName();

    final GrImportStatement importStatement =
      GroovyPsiElementFactory.getInstance(project).createImportStatementFromText(qname, true, true, null);

    final PsiFile containingFile = element.getContainingFile();
    if (!(containingFile instanceof GroovyFile)) return;
    ((GroovyFile)containingFile).addImport(importStatement);

    for (PsiReference reference : ReferencesSearch.search(resolved, new LocalSearchScope(containingFile), true)) {
      final PsiElement refElement = reference.getElement();
      if (refElement == null) continue;
      final PsiElement parent = refElement.getParent();
      if (parent instanceof GrQualifiedReference<?>) {
        GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>)parent);
      }
    }
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        if (!(element instanceof GrReferenceElement)) return false;
        final GrReferenceElement ref = (GrReferenceElement)element;
        final PsiElement parent = ref.getParent();
        if (!(parent instanceof GrReferenceElement)) return false;
        final PsiElement resolved = ref.resolve();
        if (resolved == null) return false;
        return resolved instanceof PsiClass;
      }
    };
  }
}
