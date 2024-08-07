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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocFieldReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class GroovyDocCheckInspection extends BaseInspection {
  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "GroovyDoc issues";
  }

  @Override
  protected String buildErrorString(Object... args) {
    return (String)args[0];
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitDocMethodReference(GrDocMethodReference reference) {
        checkGrDocMemberReference(reference);
      }

      @Override
      public void visitDocFieldReference(GrDocFieldReference reference) {
        checkGrDocMemberReference(reference);
      }

      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
        GroovyResolveResult resolveResult = refElement.advancedResolve();
        if (refElement.getReferenceName() == null) return;

        if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class, true, GrMember.class, GrCodeBlock.class) == null) return;

        final PsiElement resolved = resolveResult.getElement();
        if (resolved != null) return;

        final PsiElement toHighlight = refElement.getReferenceNameElement();

        registerError(toHighlight, GroovyBundle.message("cannot.resolve", refElement.getReferenceName()));
      }

      private void checkGrDocMemberReference(final GrDocMemberReference reference) {
        if (reference.resolve() != null) return;

        registerError(reference.getReferenceNameElement(), GroovyBundle.message("cannot.resolve", reference.getReferenceName()));
      }
    };
  }
}
