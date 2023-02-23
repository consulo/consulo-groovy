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

import com.intellij.java.language.impl.psi.impl.light.LightIdentifier;
import com.intellij.java.language.impl.psi.impl.light.LightVariableBase;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiIdentifier;
import com.intellij.java.language.psi.PsiModifierList;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.annotation.Nonnull;

/**
 * @author ilyas
 */
public class GrImplicitVariableImpl extends LightVariableBase implements GrImplicitVariable {
  public GrImplicitVariableImpl(PsiManager manager, PsiIdentifier nameIdentifier, @Nonnull PsiType type, boolean writable, PsiElement scope) {
    super(manager, nameIdentifier, GroovyLanguage.INSTANCE, type, writable, scope);
  }

  public GrImplicitVariableImpl(PsiManager manager, @NonNls String name, @NonNls @Nonnull String type, PsiElement scope) {
    this(manager, new GrLightIdentifier(manager, name), JavaPsiFacade.getElementFactory(manager.getProject()).
      createTypeFromText(type, scope), false, scope);
  }

  @Override
  protected PsiModifierList createModifierList() {
    return new GrLightModifierList(this);
  }

  public String toString() {
    return "Specific implicit variable: " + getName();
  }

  @Override
  @Nonnull
  public GrLightModifierList getModifierList() {
    return (GrLightModifierList)myModifierList;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another == getNavigationElement() || super.isEquivalentTo(another);
  }

  protected static class GrLightIdentifier extends LightIdentifier {
    private String myTextInternal;

    public GrLightIdentifier(PsiManager manager, String name) {
      super(manager, name);
      myTextInternal = name;
    }

    @Override
    public PsiElement replace(@Nonnull PsiElement newElement) throws IncorrectOperationException {
      myTextInternal = newElement.getText();
      return newElement;
    }

    @Override
    public String getText() {
      return myTextInternal;
    }

    @Override
    public PsiElement copy() {
      return new GrLightIdentifier(getManager(), myTextInternal);
    }
  }

}
