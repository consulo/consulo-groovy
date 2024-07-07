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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.language.ast.ASTNode;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

import jakarta.annotation.Nonnull;

/**
 * @author ilyas
 */
public class GrTypeParameterParameterExtendsListImpl extends GroovyPsiElementImpl implements GroovyPsiElement, PsiReferenceList {

  public GrTypeParameterParameterExtendsListImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type extends bounds list";
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClassType[] getReferencedTypes() {
    final GrCodeReferenceElement[] refs = findChildrenByClass(GrCodeReferenceElement.class);
    PsiClassType[] result = new PsiClassType[refs.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new GrClassReferenceType(refs[i]);
    }
    return result;
  }

  @Override
  public Role getRole() {
    return Role.EXTENDS_BOUNDS_LIST;
  }
}
