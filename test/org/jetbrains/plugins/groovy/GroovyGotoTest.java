/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.util.Condition;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */
public class GroovyGotoTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "goto/";
  }

  private void doTest(Condition<PsiElement> verifier) {
    doTest(verifier, getTestName(false) + ".groovy");
  }

  private void doTest(Condition<PsiElement> verifier, String... files) {
    for (String file : files) {
      myFixture.configureByFile(file);
    }
    final TargetElementUtilBase targetUtil = TargetElementUtilBase.getInstance();
    final PsiElement target = TargetElementUtilBase.findTargetElement(myFixture.getEditor(), targetUtil.getReferenceSearchFlags());
    assertTrue(verifier.value(target));
  }

  public void testNewExpression() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 0;
      }
    });
  }

  public void testNewExpressionWithNamedArgs() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof PsiClass;
      }
    });
  }

  public void testNewExpressionWithMapParameter() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 1;
      }
    });
  }

  public void testNewExpressionWithAnonymousClass() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof GrMethod && ((GrMethod)element).isConstructor() && ((GrMethod)element).getParameters().length == 2;
      }
    });
  }

  public void testGroovyDocParameter1() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof GrParameter && ((GrParameter)element).getName().equals("x");
      }
    });
  }

  public void testGroovyDocParameter2() throws Throwable {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof GrParameter && ((GrParameter)element).getName().equals("x");
      }
    });
  }

  public void testConstructorWithSuperClassSameName() {
    doTest(new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element instanceof PsiMethod && "p2.MyClass".equals(((PsiMethod)element).getContainingClass().getQualifiedName());
      }
    }, "p/MyClass.groovy", "p2/MyClass.groovy");
  }
}
