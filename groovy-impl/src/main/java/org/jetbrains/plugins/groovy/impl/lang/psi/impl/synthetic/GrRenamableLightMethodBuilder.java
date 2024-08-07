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

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenameableLightElement;

/**
 * @author Sergey Evdokimov
 */
public class GrRenamableLightMethodBuilder extends GrLightMethodBuilder implements GrRenameableLightElement {

  public GrRenamableLightMethodBuilder(PsiManager manager, String name) {
    super(manager, name);
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    myName = name;
    onRename(name);
    return this;
  }

  protected void onRename(@Nonnull String newName) {
    
  }
  
  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public GrLightMethodBuilder copy() {
    GrLightMethodBuilder copy = new GrRenamableLightMethodBuilder(myManager, myName);
    copyData(copy);
    return copy;
  }
}
