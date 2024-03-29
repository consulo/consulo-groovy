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
package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.language.psi.PsiType;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 01.04.2008
 */                                  
public class DynamicTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "dynamic/";
  }

  public void testMethod() throws Throwable {
    final GrReferenceExpression referenceExpression = doDynamicFix();

    final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
    final String[] methodArgumentsNames = QuickfixUtil.getMethodArgumentsNames(getProject(), psiTypes);
    final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

    assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)));
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(getTestName(false));
    assertNotNull(classElement);
    return classElement;
  }

  public void testProperty() throws Throwable {
    final String name = doDynamicFix().getReferenceName();
    assert getDClassElement().getPropertyByName(name) != null;
  }

  private GrReferenceExpression doDynamicFix() throws Throwable {
    myFixture.enableInspections(new GrUnresolvedAccessInspection());

    final List<IntentionAction> actions = myFixture.getAvailableIntentions(getTestName(false) + ".groovy");

    DynamicPropertyFix dynamicFix = ContainerUtil.findInstance(actions, DynamicPropertyFix.class);
    if (dynamicFix != null) {
      dynamicFix.invoke(getProject());
      return dynamicFix.getReferenceExpression();
    }
    else {
      final DynamicMethodFix fix = ContainerUtil.findInstance(actions, DynamicMethodFix.class);
      assertNotNull(fix);
      fix.invoke(getProject());
      return fix.getReferenceExpression();
    }
  }

}
