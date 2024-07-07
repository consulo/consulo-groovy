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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.action.TypeDeclarationProvider;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl(order = "before JavaTypeDeclarationProvider")
public class GroovyTypeDeclarationProvider extends TypeDeclarationProvider {
  @RequiredReadAction
  @Nullable
  @Override
  public PsiElement[] getSymbolTypeDeclarations(@Nonnull PsiElement targetElement, @Nullable Editor editor, int offset) {
    PsiType type;
    if (targetElement instanceof GrVariable) {
      type = ((GrVariable)targetElement).getTypeGroovy();
    }
    else if (targetElement instanceof GrMethod) {
      type = ((GrMethod)targetElement).getInferredReturnType();
    }
    else {
      return null;
    }
    if (type == null) {
      return null;
    }
    PsiClass psiClass = PsiUtil.resolveClassInType(type);
    return psiClass == null ? null : new PsiElement[]{psiClass};
  }
}
