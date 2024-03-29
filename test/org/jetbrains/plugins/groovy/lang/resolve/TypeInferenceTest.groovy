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
package org.jetbrains.plugins.groovy.lang.resolve;


import com.intellij.java.language.psi.PsiIntersectionType
import com.intellij.java.language.psi.PsiReference
import com.intellij.java.language.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.util.TestUtils

import static com.intellij.java.language.psi.CommonClassNames.*

/**
 * @author ven
 */
public class TypeInferenceTest extends GroovyResolveTestCase {
  final String basePath = TestUtils.testDataPath + "resolve/inference/"

  public void testTryFinallyFlow() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow/A.groovy").element;
    final PsiType type = ref.type;
    assertTrue(type instanceof PsiIntersectionType);
    final PsiType[] conjuncts = ((PsiIntersectionType)type).conjuncts;
    assertEquals(conjuncts.length, 2);
  }

  public void testTryFinallyFlow1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow1/A.groovy").element;
    final PsiType type = ref.type;
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testTryFinallyFlow2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("tryFinallyFlow2/A.groovy").element;
    final PsiType type = ref.type;
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.Integer"));
  }

  public void testThrowVariable() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("throwVariable/A.groovy").element;
    final PsiType type = ref.type;
    assertNotNull(type);
    assertEquals("java.lang.Exception", type.canonicalText);
  }

  public void testGrvy852() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy852/A.groovy").element;
    final PsiType type = ref.type;
    assertNotNull(type);
    assertEquals("java.lang.Object", type.canonicalText);
  }

  public void testGenericMethod() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericMethod/A.groovy").element;
    final PsiType type = ref.type;
    assertNotNull(type);
    assertEquals("java.util.List<java.lang.String>", type.canonicalText);
  }

  public void testCircular() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular/A.groovy").element;
    assertNull(ref.type);
  }

  public void testCircular1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("circular1/A.groovy").element;
    assertNull(ref.type);
  }

  public void testClosure() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure/A.groovy").element;
    assertNotNull(ref.type);
  }

  public void testClosure1() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure1/A.groovy").element;
    assertTrue(ref.type.equalsToText("java.lang.Integer"));
  }

  public void testClosure2() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("closure2/A.groovy").element;
    assertTrue(ref.type.equalsToText("java.lang.Integer"));
  }

  public void testGrvy1209() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("grvy1209/A.groovy").element;
    assertTrue(ref.type.equalsToText("java.lang.String"));
  }

  public void testLeastUpperBoundClosureType() {
    GrReferenceExpression ref = (GrReferenceExpression)configureByFile("leastUpperBoundClosureType/A.groovy").element;
    assertInstanceOf(ref.type, GrClosureType.class);
  }

  public void testJavaLangClassType() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("javaLangClassType/A.groovy").element;
    assertEquals("java.lang.String", ref.type.canonicalText);
  }

  public void testGenericWildcard() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("genericWildcard/A.groovy").element;
    assertEquals("A<Base>", ref.type.canonicalText);
  }

  public void testArrayLikeAccessWithIntSequence() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayLikeAccessWithIntSequence/A.groovy").element;
    assertEquals("java.util.List<java.lang.Integer>", ref.type.canonicalText);
  }

  public void testArrayAccess() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("arrayAccess/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.type.canonicalText);
  }

  public void testReturnTypeByTailExpression() {
    final GrReferenceExpression ref = (GrReferenceExpression)configureByFile("returnTypeByTailExpression/A.groovy");
    assertEquals(JAVA_LANG_STRING, ref.type.canonicalText);
  }

  public void testParameterWithBuiltinType() {
    GrReferenceExpression refExpr = (GrReferenceExpression)configureByFile("parameterWithBuiltinType/A.groovy");
    assertEquals("java.lang.Integer", refExpr.type.canonicalText);
  }

  public void testRawTypeInReturnExpression() {
    assertNotNull(resolve("A.groovy"));
  }

  public void testMethodCallInvokedOnArrayAccess() {
    final GrReferenceExpression reference = (GrReferenceExpression)configureByFile("A.groovy");
    assertNotNull(reference)
    assertNotNull(reference.type)
    assertEquals("java.lang.Integer", reference.type.canonicalText);
  }

  private void assertTypeEquals(String expected, String fileName) {
    final PsiReference ref = configureByFile(getTestName(true) + "/" + fileName);
    assertInstanceOf(ref, GrReferenceExpression.class);
    final PsiType type = ((GrReferenceExpression)ref).type;
    assertNotNull(type);
    assertEquals(expected, type.canonicalText);
  }

  public void testTypeOfGroupBy() {
    assertTypeEquals("java.util.Map<java.lang.Integer,java.util.List<java.lang.Integer>>", "A.groovy");
  }

  public void testConditionalExpressionWithNumericTypes() {
    assertTypeEquals("java.math.BigDecimal", "A.groovy");
  }

  public void testImplicitCallMethod() {
    assertEquals("java.lang.String", ((GrExpression)configureByFile("A.groovy")).type.canonicalText);
  }

  public void testTupleWithNullInIt() {
    assertTypeEquals("java.util.ArrayList", "A.groovy");
  }

  public void testImplicitlyReturnedMethodCall() {
    assertTypeEquals("java.util.Map<BasicRange,java.util.Map<BasicRange,java.lang.Double>>", "A.groovy");
  }

  public void testInferWithClosureType() {
    assertTypeEquals("java.util.Date", "A.groovy");
  }

  public void testPlusEquals1() {
    assertTypeEquals("Test", "A.groovy");
  }

  public void testPlusEquals2() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testPlusEquals3() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testPlusEqualsClosure() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testGetAtClosure() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testPreferMethodOverloader() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testSafeInvocationInClassQualifier() {
    assertTypeEquals("java.lang.Class", "SafeInvocationInClassQualifier.groovy");
  }

  public void testReturnTypeFromMethodClosure() {
    assertTypeEquals("java.lang.String", "A.groovy");
  }

  public void testNoSOF() {
    final PsiReference ref = configureByFile(getTestName(true) + "/A.groovy");
    assertInstanceOf(ref, GrReferenceExpression.class);
    final PsiType type = ((GrReferenceExpression)ref).type;
    assertNull(type);
  }

  public void testTraditionalForVar() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testIncMethod() {
    assertTypeEquals(JAVA_LANG_INTEGER, "A.groovy");
  }

  public void testDGMFind() {
    assertTypeEquals("java.io.File", "a.groovy");
  }

  public void testMultiTypeParameter() {
    assertTypeEquals("X | Y", "a.groovy");
  }

  public void testTypeArgsInAccessor() {
    assertTypeEquals("Foo<java.lang.String>", "a.groovy");
  }

  public void testSingleParameterInStringInjection() {
    assertTypeEquals("java.io.StringWriter", "a.groovy");
  }

  void testIndexPropertyPlusAssigned() {
    GroovyFile file = myFixture.configureByText('a.groovy', '''
class X {
    def putAt(String s, X x){new Date()}

    def getAt(String s) {new X()}

    def plus(X x, int i) {x}
}

map = new X()

map['i'] += 2
''') as GroovyFile
    GrAssignmentExpression assignment = file.topStatements[2] as GrAssignmentExpression
    assertTrue(assignment.LValue.type.equalsToText(JAVA_UTIL_DATE))
  }

  void testAllTypeParamsAreSubstituted() {
    assertTypeEquals('java.util.Map', 'a.groovy')
  }

  void testDiamond() {
    GroovyFile file = myFixture.configureByText('a.groovy', '''
List<String> list = new ArrayList<>()
List<Integer> l2

(list, l2) = [new ArrayList<>(), new ArrayList<>()]
''') as GroovyFile

    def statements = file.topStatements

    assertEquals('java.util.ArrayList<java.lang.String>', (statements[0] as GrVariableDeclaration).variables[0].initializerGroovy.type.canonicalText)
    assertEquals('java.util.ArrayList<java.lang.String>', ((statements[2] as GrAssignmentExpression).RValue as GrListOrMap).initializers[0].type.canonicalText)
    assertEquals('java.util.ArrayList<java.lang.Integer>', ((statements[2] as GrAssignmentExpression).RValue as GrListOrMap).initializers[1].type.canonicalText)
  }

  void testWildCardsNormalized() {
    assertTypeEquals(Object.canonicalName, 'a.groovy')
  }

  void testIndexPropertyInLHS() {
    assertTypeEquals("java.util.Map", 'a.groovy')
  }

  void testEmptyMapTypeArgs() {
    myFixture.configureByText('a.groovy', '''
class X<A, B> implements Map<A, B> {}

X<String, Integer> x = [:]
''')

    def type = ((myFixture.file as GroovyFile).statements[0] as GrVariableDeclaration).variables[0].initializerGroovy.type
    assertEquals("java.util.Map<java.lang.String,java.lang.Integer>", type.canonicalText)
  }

  void testRawCollectionsInCasts() {
    doTest('''\
String[] a = ["a"]
def b = a as ArrayList
def cc = b[0]
print c<caret>c''', String.canonicalName)
  }

  void testFind() {
    doTest('''\
def arr =  ['1', '2', '3'] as String[]
def found = arr.find({it=='1'})
print fou<caret>nd''', String.canonicalName)
  }

  void testFindAll() {
    doTest('''\
def arr =  ['1', '2', '3']
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.ArrayList<java.lang.String>')
  }

  void testFindAllForArray() {
    doTest('''\
def arr =  ['1', '2', '3'] as String[]
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.ArrayList<java.lang.String>')
  }

  void testFindAllForSet() {
    myFixture.addClass('''\
package java.util;
class HashSet<T> implements Set<T> {} ''')
    doTest('''\
def arr =  ['1', '2', '3'] as Set<String>
def found = arr.findAll({it==1})
print fou<caret>nd''', 'java.util.HashSet<java.lang.String>')
  }

  void testInferArgumentTypeFromMethod1() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    <caret>a.substring(2)
  }
}
''', '[java.lang.String,java.lang.Integer]')
  }

  void testInferArgumentTypeFromMethod2() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    <caret>a.substring(2)
}
''', '[java.lang.String,java.lang.Integer]')
  }

  void testInferArgumentTypeFromMethod3() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
    bar(a)
    print a
    <caret>a.substring(2)
}
''', '[java.lang.String,java.lang.Integer]')
  }

  void testInferArgumentTypeFromMethod4() {
    doTest('''\
def bar(String s) {}

def foo(Integer a) {
  while(true) {
    bar(a)
    print a
    <caret>a.substring(2)
  }
}
''', '[java.lang.String,java.lang.Integer]')
  }

  void testEmptyListOrListWithGenerics() {
    doTest('''\
def list = cond ? [1, 2, 3] : []
print lis<caret>t
''', "$JAVA_UTIL_LIST<$JAVA_LANG_INTEGER>")
  }

  void testEmptyListOrListWithGenerics2() {
    doTest('''\
def List<Integer> foo(){}
def list = cond ? foo() : []
print lis<caret>t
''', "$JAVA_UTIL_LIST<$JAVA_LANG_INTEGER>")
  }

  void testEmptyMapOrMapWithGenerics() {
    doTest('''\
def map = cond ? [1:'a', 2:'a', 3:'a'] : [:]
print ma<caret>p
''', "$JAVA_UTIL_MAP<$JAVA_LANG_STRING, $JAVA_LANG_STRING>")
  }

  void testEmptyMapOrMapWithGenerics2() {
    doTest('''\
def Map<String, String> foo(){}
def map = cond ? foo() : [:]
print ma<caret>p
''', "$JAVA_UTIL_MAP<$JAVA_LANG_STRING,$JAVA_LANG_STRING>")
  }

  void testSpread1() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
[new A()].stri<caret>ng
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_STRING>")
  }

  void testSpread2() {

    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
class Cat {
  static getFoo(String b) {2}
}
use(Cat) {
  [new A()].string.fo<caret>o
}
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_INTEGER>")
  }

  void testSpread3() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
[[new A()]].stri<caret>ng
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_STRING>>")
  }

  void testSpread4() {
    myFixture.addClass('''\
class A {
  String getString() {return "a";}
}''')
    doTest('''\
class Cat {
  static getFoo(String b) {2}
}

use(Cat){
  [[new A()]].string.fo<caret>o
}
''', "$JAVA_UTIL_ARRAY_LIST<$JAVA_UTIL_ARRAY_LIST<$JAVA_LANG_INTEGER>>")
  }

  void testInstanceOfInferring1() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String || oo != null
  o<caret>o
}
''', null)
  }

  void testInstanceOfInferring2() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String || o<caret>o != null
  oo
}
''', null)
  }

  void testInstanceOfInferring3() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String && o<caret>o != null
  oo
}
''', String.canonicalName)
  }

  void testInstanceOfInferring4() {
    doTest('''\
def bar(oo) {
  boolean b = oo instanceof String && oo != null
  o<caret>o
}
''', null)
  }

  void testInstanceOfInferring5() {
    doTest('''\
def foo(def oo) {
  if (oo instanceof String && oo instanceof CharSequence) {
    oo
  }
  else {
    o<caret>o
  }

}
''', null)
  }

  void testInstanceOfInferring6() {
    doTest('''\
def foo(bar) {
  if (!(bar instanceof String) && bar instanceof Runnable) {
    ba<caret>r
  }
}''', 'java.lang.Runnable')
  }

  void testInString() {
    doTest '''\
def foo(ii) {
  if (ii in String)
    print i<caret>i
}''', 'java.lang.String'
  }

  void testIndexProperty() {
    doTest('''\
private void getCommonAncestor() {
    def c1 = [new File('a')]
    for (int i = 0; i < 2; i++) {
        if (c1[i] != null) break
        def cur = c1[i]
        print cu<caret>r
    }
}
''', 'java.io.File')

  }

  void testWildcardClosureParam() {
    doTest('''\
class Tx {
    def methodOfT() {}
}

def method(List<? extends Tx> t) {
    t.collect { print i<caret>t }
}
''', 'Tx')
  }

  void testAssert() {
    doTest('''\
def foo(def var) {
  assert var instanceof String
  va<caret>r.isEmpty()
}
''', 'java.lang.String')
  }

  void testUnresolvedSpread() {
    doTest('''\
def xxx = abc*.name
print xx<caret>x''', 'java.util.List')
  }

  void testThisInCategoryClass() {
    doTest('''\
class Cat {}

@groovy.lang.Category(Cat)
class Any {
  void foo() {
    print th<caret>is
  }
}
''', 'Cat')
  }

  private void doTest(String text, String type) {
    def file = myFixture.configureByText('_.groovy', text)
    def ref = file.findReferenceAt(myFixture.editor.caretModel.offset) as GrReferenceExpression
    def actual = ref.type
    if (type == null) {
      assertNull(actual)
      return
    }

    assertNotNull(actual)
    if (actual instanceof PsiIntersectionType) {
      assertEquals(type, genIntersectionTypeText(actual))
    }
    else {
      assertEquals(type, actual.canonicalText)
    }
  }

  private static String genIntersectionTypeText(PsiIntersectionType t) {
    StringBuilder b = new StringBuilder('[')
    for (PsiType c : t.conjuncts) {
      b.append(c.canonicalText).append(',')
    }
    if (t.conjuncts) {
      b.replace(b.length() - 1, b.length(), ']')
    }
    return b.toString()
  }

}
