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

package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.ImportClassFixBase;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMember;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.Comparing;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import jakarta.annotation.Nonnull;
import java.util.List;

/**
 * @author peter
 */
public class GroovyAddImportAction extends ImportClassFixBase<GrReferenceElement, GrReferenceElement> {
  public GroovyAddImportAction(GrReferenceElement ref) {
    super(ref, ref);
  }

  @Override
  protected String getReferenceName(@Nonnull GrReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected PsiElement getReferenceNameElement(@Nonnull GrReferenceElement reference) {
    return reference.getReferenceNameElement();
  }

  @Override
  protected boolean hasTypeParameters(@Nonnull GrReferenceElement reference) {
    return reference.getTypeArguments().length > 0;
  }

  @Override
  protected String getQualifiedName(GrReferenceElement reference) {
    return reference.getCanonicalText();
  }

  @Override
  protected boolean isQualified(GrReferenceElement reference) {
    return reference.getQualifier() != null;
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(PsiFile psiFile, String name) {
    if (!(psiFile instanceof GroovyFile)) return false;
    final GrImportStatement[] importStatements = ((GroovyFile)psiFile).getImportStatements();
    for (GrImportStatement importStatement : importStatements) {
      final GrCodeReferenceElement importReference = importStatement.getImportReference();
      if (importReference == null || importReference.resolve() != null) {
        continue;
      }
      if (importStatement.isOnDemand() || Comparing.strEqual(importStatement.getImportedName(), name)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected List<PsiClass> filterByContext(List<PsiClass> candidates, GrReferenceElement ref) {
    PsiElement typeElement = ref.getParent();
    if (typeElement instanceof GrTypeElement) {
      PsiElement decl = typeElement.getParent();
      if (decl instanceof GrVariableDeclaration) {
        GrVariable[] vars = ((GrVariableDeclaration)decl).getVariables();
        if (vars.length == 1) {
          PsiExpression initializer = vars[0].getInitializer();
          if (initializer != null) {
            return filterAssignableFrom(initializer.getType(), candidates);
          }
        }
      }
    }

    return super.filterByContext(candidates, ref);
  }

  @Override
  protected String getRequiredMemberName(GrReferenceElement reference) {
    if (reference.getParent() instanceof GrReferenceElement) {
      return ((GrReferenceElement)reference.getParent()).getReferenceName();
    }
    return super.getRequiredMemberName(reference);
  }

  protected boolean isAccessible(PsiMember member, GrReferenceElement reference) {
    return true;
  }
}
