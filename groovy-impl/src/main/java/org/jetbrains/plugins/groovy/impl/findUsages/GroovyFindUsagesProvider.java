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
package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.cacheBuilder.WordsScanner;
import consulo.language.findUsage.FindUsagesProvider;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.impl.refactoring.rename.PropertyForRename;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyFindUsagesProvider implements FindUsagesProvider {

  public static final GroovyFindUsagesProvider INSTANCE = new GroovyFindUsagesProvider();

  public GroovyFindUsagesProvider() {
  }

  @Nullable
  public WordsScanner getWordsScanner() {
    return new GroovyWordsScanner();
  }

  public boolean canFindUsagesFor(@Nonnull PsiElement psiElement) {
    return psiElement instanceof PsiClass ||
      psiElement instanceof PsiMethod ||
      psiElement instanceof GrVariable;
  }

  @Nullable
  public String getHelpId(@Nonnull PsiElement psiElement) {
    return null;
  }

  @Nonnull
  public String getType(@Nonnull PsiElement element) {
    if (element instanceof PsiClass) return "class";
    if (element instanceof PsiMethod) return "method";
    if (element instanceof PsiField) return "field";
    if (element instanceof PsiParameter) return "parameter";
    if (element instanceof GrBindingVariable) return "script binding variable";
    if (element instanceof PsiVariable) return "variable";
    if (element instanceof GrLabeledStatement) return "label";
    if (element instanceof PropertyForRename) return "property";
    if (element instanceof GrClosableBlock) return "closure";
    return "";
  }

  @Nonnull
  public String getDescriptiveName(@Nonnull PsiElement element) {
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      String qName = aClass.getQualifiedName();
      return qName == null ? "" : qName;
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      String result = PsiFormatUtil.formatMethod(method,
                                                 PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                                 PsiFormatUtil.SHOW_TYPE);
      final PsiClass clazz = method.getContainingClass();
      if (clazz != null) {
        result += " of " + getDescriptiveName(clazz);
      }

      return result;
    }
    else if (element instanceof PsiVariable) {
      final String name = ((PsiVariable)element).getName();
      if (name != null) {
        return name;
      }
    }
    else if (element instanceof GrLabeledStatement) {
      return ((GrLabeledStatement)element).getName();
    }
    else if (element instanceof PropertyForRename) {
      return ((PropertyForRename)element).getPropertyName();
    }
    else if (element instanceof GrClosableBlock) {
      return "closure";
    }

    return "";
  }

  @Nonnull
  public String getNodeText(@Nonnull PsiElement element, boolean useFullName) {
    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getQualifiedName();
      if (name == null || !useFullName) {
        name = ((PsiClass)element).getName();
      }
      if (name != null) return name;
    }
    else if (element instanceof PsiMethod) {
      return PsiFormatUtil.formatMethod((PsiMethod)element,
                                        PsiSubstitutor.EMPTY,
                                        PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                        PsiFormatUtil.SHOW_TYPE);

    }
    else if (element instanceof PsiVariable) {
      final String name = ((PsiVariable)element).getName();
      if (name != null) {
        return name;
      }
    }

    return "";
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
