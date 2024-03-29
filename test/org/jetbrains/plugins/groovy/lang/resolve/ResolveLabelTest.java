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

package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.java.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Maxim.Medvedev
 */

public class ResolveLabelTest extends GroovyResolveTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath()+"resolve/label";
  }

  public void testLabelResolve() throws Exception {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertInstanceOf(resolved, GrLabeledStatement.class);
  }

  public void testLabelResolve2() throws Exception {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNotNull(resolved);
    assertInstanceOf(resolved, GrLabeledStatement.class);
  }

  public void testLabelNotResolved() throws Exception {
    final PsiReference ref = configureByFile(getTestName(true)+"/"+getTestName(false) + ".groovy");
    final PsiElement resolved = ref.resolve();
    assertNull(resolved);
  }
}
