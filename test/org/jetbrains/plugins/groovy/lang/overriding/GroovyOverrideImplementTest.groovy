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
package org.jetbrains.plugins.groovy.lang.overriding
import com.intellij.codeInsight.generation.OverrideImplementUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.java.language.psi.JavaPsiFacade
import com.intellij.java.language.psi.PsiClassOwner
import com.intellij.java.language.psi.PsiMethod
import com.intellij.java.language.psi.impl.source.PostprocessReformattingAspect
import com.intellij.java.language.psi.search.GlobalSearchScope
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
/**
 * @author peter
 */
public class GroovyOverrideImplementTest extends LightGroovyTestCase {

  public void testInEmptyBraces() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "equals"))
    myFixture.checkResult """
class Test {
    @Override
    boolean equals(Object obj) {
        return super.equals(obj)
    }
}
"""
  }
  
  public void testConstructor() throws Exception {
    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod(Object.name, "Object"))
    myFixture.checkResult """
class Test {
    Test() {
        super()
    }
}
"""
  }

  public void testNoSuperReturnType() throws Exception {
    myFixture.addFileToProject("Foo.groovy", """
    class Foo {
      def foo() {
        true
      }
    }""")

    myFixture.configureByText "a.groovy", """
class Test {<caret>}
"""
    generateImplementation(findMethod("Foo", "foo"))
    myFixture.checkResult """
class Test {
    @Override
    def foo() {
        return super.foo()
    }
}
"""
  }

  public void testMethodTypeParameters() {
    myFixture.addFileToProject "v.java", """
class Base<E> {
  public <T> T[] toArray(T[] t) {return (T[])new Object[0];}
}
"""
    myFixture.configureByText "a.groovy", """
class Test<T> extends Base<T> {<caret>}
"""
    generateImplementation(findMethod("Base", "toArray"))
    myFixture.checkResult """
class Test<T> extends Base<T> {
    @Override
    def <T> T[] toArray(T[] t) {
        return super.toArray(t)
    }
}
"""
  }

  void testTrhowsList() {
    myFixture.configureByText('a.groovy', '''\
class X implements I {
    <caret>
}

interface I {
    void foo() throws RuntimeException
}
''')

    generateImplementation(findMethod('I', 'foo'))

    myFixture.checkResult('''\
class X implements I {

    @Override
    void foo() throws RuntimeException {

    }
}

interface I {
    void foo() throws RuntimeException
}
''')
  }

  public void _testImplementIntention() {
    myFixture.configureByText('a.groovy', '''
class Base<E> {
  public <E> E fo<caret>o(E e){}
}

class Test extends Base<String> {
}
''')

    def fixes = myFixture.getAvailableIntentions()
    assertSize(1, fixes)

    def fix = fixes[0]
    fix.invoke(myFixture.project, myFixture.editor, myFixture.file)
  }

  private def generateImplementation(PsiMethod method) {
    ApplicationManager.application.runWriteAction {
      GrTypeDefinition clazz = (myFixture.file as PsiClassOwner).classes[0] as GrTypeDefinition
      OverrideImplementUtil.overrideOrImplement(clazz, method);
      PostprocessReformattingAspect.getInstance(myFixture.project).doPostponedFormatting()
    }
    myFixture.editor.selectionModel.removeSelection()
  }

  PsiMethod findMethod(String className, String methodName) {
    return JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project)).findMethodsByName(methodName, false)[0]
  }

  @Override
  protected String getBasePath() {
    return null
  }
}
