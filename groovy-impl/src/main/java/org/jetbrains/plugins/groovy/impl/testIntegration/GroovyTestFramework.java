/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.testIntegration;

import com.intellij.java.execution.impl.junit.JUnitUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.InheritanceUtil;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.testIntegration.JavaTestFramework;
import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplateDescriptor;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.ui.image.Image;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyTestFramework extends JavaTestFramework {
  private static final Logger LOG = Logger.getInstance(GroovyTestFramework.class);

  @Override
  protected String getMarkerClassFQName() {
    return GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE;
  }

  @Override
  protected boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    return clazz.getLanguage() == GroovyFileType.GROOVY_LANGUAGE &&
      JUnitUtil.isTestClass(clazz) &&
      InheritanceUtil.isInheritor(clazz, GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE);
  }

  @Override
  protected PsiMethod findSetUpMethod(@Nonnull PsiClass clazz) {
    for (PsiMethod method : clazz.getMethods()) {
      if (method.getName().equals("setUp")) {
        return method;
      }
    }
    return null;
  }

  @Override
  protected PsiMethod findTearDownMethod(@Nonnull PsiClass clazz) {
    for (PsiMethod method : clazz.getMethods()) {
      if (method.getName().equals("tearDown")) {
        return method;
      }
    }
    return null;
  }

  @Override
  protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    LOG.assertTrue(clazz.getLanguage() == GroovyFileType.GROOVY_LANGUAGE);
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(clazz.getProject());

    final PsiMethod patternMethod = createSetUpPatternMethod(factory);

    final PsiClass baseClass = clazz.getSuperClass();
    if (baseClass != null) {
      final PsiMethod baseMethod = baseClass.findMethodBySignature(patternMethod, false);
      if (baseMethod != null && baseMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PROTECTED, false);
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PUBLIC, true);
      }
    }

    PsiMethod inClass = clazz.findMethodBySignature(patternMethod, false);
    if (inClass == null) {
      PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
      if (testMethod != null) {
        return (PsiMethod)clazz.addBefore(patternMethod, testMethod);
      }
      return (PsiMethod)clazz.add(patternMethod);
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  @Override
  public char getMnemonic() {
    return 'G';
  }

  @Nonnull
  @Override
  public String getName() {
    return "Groovy JUnit";
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public String getDefaultSuperClass() {
    return GroovyCommonClassNames.GROOVY_UTIL_TEST_CASE;
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Groovy JUnit SetUp Method.groovy");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Groovy JUnit TearDown Method.groovy");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Groovy JUnit Test Method.groovy");
  }

  @Override
  public boolean isTestMethod(PsiElement element) {
    return element instanceof PsiMethod && JUnitUtil.getTestMethod(element) != null;
  }

  @Override
  @Nonnull
  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
  }
}
