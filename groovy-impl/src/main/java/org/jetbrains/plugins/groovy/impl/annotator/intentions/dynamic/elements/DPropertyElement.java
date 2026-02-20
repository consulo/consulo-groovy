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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.elements;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiVariable;
import consulo.language.psi.PsiManager;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.GrDynamicImplicitProperty;

import jakarta.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DPropertyElement extends DItemElement {
  private GrDynamicImplicitProperty myPsi;

  //Do not use directly! Persistence component uses default constructor for deserializable
  @SuppressWarnings("UnusedDeclaration")
  public DPropertyElement() {
    super(null, null, null);
  }

  public DPropertyElement(Boolean isStatic, String name, String type) {
    super(isStatic, name, type);
  }

  @Override
  public void clearCache() {
    myPsi = null;
  }

  @Override
  @Nonnull
  public PsiVariable getPsi(PsiManager manager, String containingClassName) {
    if (myPsi != null) {
      return myPsi;
    }

    Boolean isStatic = isStatic();

    String type = getType();
    if (type == null || type.trim().isEmpty()) {
      type = CommonClassNames.JAVA_LANG_OBJECT;
    }
    myPsi = new GrDynamicImplicitProperty(manager, getName(), type, containingClassName);

    if (isStatic != null && isStatic.booleanValue()) {
      myPsi.getModifierList().addModifier(PsiModifier.STATIC);
    }

    return myPsi;
  }
}
