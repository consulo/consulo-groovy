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
package org.jetbrains.plugins.groovy.impl.refactoring.rename.inplace;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.rename.inplace.VariableInplaceRenameHandler;
import consulo.language.editor.refactoring.rename.inplace.VariableInplaceRenamer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.scope.LocalSearchScope;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GrVariableInplaceRenameHandler extends VariableInplaceRenameHandler {
  @Override
  protected boolean isAvailable(PsiElement element, Editor editor, PsiFile file) {
    if (!editor.getSettings().isVariableInplaceRenameEnabled()) return false;

    if (!(element instanceof GrVariable)) return false;
    if (element instanceof GrField) return false;

    final SearchScope scope = element.getUseScope();
    if (!(scope instanceof LocalSearchScope)) return false;

    final PsiElement[] scopeElements = ((LocalSearchScope)scope).getScope();
    return scopeElements.length == 1 ||
      scopeElements.length == 2 && (scopeElements[0] instanceof GrDocComment ^ scopeElements[1] instanceof GrDocComment);
  }

  @Override
  protected VariableInplaceRenamer createRenamer(@Nonnull PsiElement elementToRename, Editor editor) {
    return new GrVariableInplaceRenamer((PsiNameIdentifierOwner)elementToRename, editor);
  }
}
