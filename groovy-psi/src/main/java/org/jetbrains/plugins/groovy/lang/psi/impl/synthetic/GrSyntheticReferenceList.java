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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.language.impl.psi.LightElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;

import jakarta.annotation.Nonnull;

/**
 * Created by Max Medvedev on 10/1/13
 */
public class GrSyntheticReferenceList extends LightElement implements PsiReferenceList {
  private final GrReferenceList myList;
  private final Role myRole;

  public GrSyntheticReferenceList(GrReferenceList list, Role role) {
    super(list.getManager(), list.getLanguage());
    myList = list;
    myRole = role;
  }

  @Override
  public String toString() {
    return "Synthetic reference list";
  }

  @Nonnull
  @Override
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiClassType[] getReferencedTypes() {
    return myList.getReferencedTypes();
  }

  @Override
  public Role getRole() {
    return myRole;
  }
}
