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
package org.jetbrains.plugins.groovy.impl.lang.psi.impl.synthetic;

import com.intellij.java.language.psi.JavaElementVisitor;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import com.intellij.java.language.psi.PsiReferenceList;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author ven
 */
public class LightReferenceList extends LightElement implements PsiReferenceList {
  protected LightReferenceList(PsiManager manager) {
    super(manager, GroovyLanguage.INSTANCE);
  }

  @Override
  public void accept(@Nonnull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor) visitor).visitReferenceList(this);
    }
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    return PsiJavaCodeReferenceElement.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClassType[] getReferencedTypes() {
    return PsiClassType.EMPTY_ARRAY;
  }

  @Override
  public Role getRole() {
    return Role.THROWS_LIST;
  }

  public String toString() {
    return "Light Reference List";
  }
}
