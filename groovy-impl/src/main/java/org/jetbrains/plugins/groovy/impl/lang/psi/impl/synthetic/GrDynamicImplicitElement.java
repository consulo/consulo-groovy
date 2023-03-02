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

import com.intellij.java.language.psi.PsiClass;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiFile;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ilyas
 */
public interface GrDynamicImplicitElement extends ItemPresentation, NavigationItem
{
  @Nullable
  public PsiClass getContainingClassElement();

  @Nonnull
  public SearchScope getUseScope();

  public PsiFile getContainingFile();

  public String getContainingClassName();
}
