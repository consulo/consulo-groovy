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
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.java.language.psi.PsiFile
import com.intellij.java.language.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
public abstract class GroovyParsingTestCase extends LightCodeInsightFixtureTestCase {

  String getBasePath() {
    TestUtils.testDataPath + "parsing/groovy/"
  }

  public void doTest() {
    doTest(getTestName(true).replace('$', '/') + ".test");
  }

  protected void doTest(String fileName) {
    def (String input, String output) = TestUtils.readInput(testDataPath + "/" + fileName);
    checkParsing(input, output);
  }

  protected void checkParsing(String input, String output) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(project, input);
    final String psiTree = DebugUtil.psiToString(psiFile, false);
    final String prefix = input.trim() + '\n-----\n';
    assertEquals(prefix + output.trim(), prefix + psiTree.trim());
  }
}
