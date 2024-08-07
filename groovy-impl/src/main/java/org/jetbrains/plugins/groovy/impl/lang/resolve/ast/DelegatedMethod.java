/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.lang.resolve.ast;

import com.intellij.java.language.impl.psi.impl.light.LightMethod;
import com.intellij.java.language.psi.OriginInfoAwareElement;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiMirrorElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Max Medvedev
 */
public class DelegatedMethod extends LightMethod implements PsiMethod, PsiMirrorElement, OriginInfoAwareElement {
  private final PsiMethod myPrototype;

  public DelegatedMethod(@Nonnull PsiMethod delegate, @Nonnull PsiMethod prototype) {
    super(prototype.getManager(), delegate, delegate.getContainingClass(), delegate.getLanguage());
    myPrototype = prototype;
    setNavigationElement(myPrototype);
  }

  @Nonnull
  @Override
  public PsiMethod getPrototype() {
    return myPrototype;
  }

  @Nullable
  @Override
  public String getOriginInfo() {
    PsiClass aClass = myPrototype.getContainingClass();
    if (aClass != null && aClass.getName() != null) {
      return "delegates to " + aClass.getName();
    }
    return null;
  }
}
