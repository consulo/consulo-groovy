/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.dgm;

import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.java.impl.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class DGMReferenceContributor extends PsiReferenceContributor {

  private final JavaClassReferenceProvider myProvider = new JavaClassReferenceProvider();

  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PropertiesTokenTypes.VALUE_CHARACTERS), new PsiReferenceProvider() {
      @Nonnull
      @Override
      public PsiReference[] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
        if (!DGMUtil.isInDGMFile(element)) return PsiReference.EMPTY_ARRAY;

        IProperty parent = (IProperty)element.getParent();
        if (!"extensionClasses".equals(parent.getName())) {
          return PsiReference.EMPTY_ARRAY;
        }

        ArrayList<PsiReference> result = new ArrayList<PsiReference>();

        String text = element.getText();

        int i = 0;
        while ((i = skipWhiteSpace(i, text)) < text.length()) {
          int end = findWhiteSpaceOrComma(i, text);
          if (end <= text.length()) {
            JavaClassReferenceSet set = new JavaClassReferenceSet(text.substring(i, end), element, i, true, myProvider);
            ContainerUtil.addAll(result, set.getAllReferences());
          }
          i = end;
          i = skipWhiteSpace(i, text);
          if (i == text.length()) break;
          if (text.charAt(i) == ',') i++;
          i = skipWhiteSpace(i, text);
        }

        return result.toArray(new PsiReference[result.size()]);
      }
    });
  }

  private static int skipWhiteSpace(int i, String text) {
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      i++;
    }
    return i;
  }

  private static int findWhiteSpaceOrComma(int i, String text) {
    while (i < text.length() && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != ',') {
      i++;
    }
    return i;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PropertiesLanguage.INSTANCE;
  }
}
