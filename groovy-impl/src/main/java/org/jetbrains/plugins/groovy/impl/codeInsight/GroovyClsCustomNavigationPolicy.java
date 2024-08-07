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
package org.jetbrains.plugins.groovy.impl.codeInsight;

import com.intellij.java.language.impl.psi.impl.compiled.ClsCustomNavigationPolicyEx;
import com.intellij.java.language.impl.psi.impl.compiled.ClsMethodImpl;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyClsCustomNavigationPolicy extends ClsCustomNavigationPolicyEx {
  @Override
  @Nullable
  public PsiElement getNavigationElement(@Nonnull ClsMethodImpl clsMethod) {
    PsiMethod source = clsMethod.getSourceMirrorMethod();
    if (source instanceof LightElement && source.getLanguage() == GroovyFileType.GROOVY_LANGUAGE) {
      return source.getNavigationElement();
    }

    return null;
  }
}
