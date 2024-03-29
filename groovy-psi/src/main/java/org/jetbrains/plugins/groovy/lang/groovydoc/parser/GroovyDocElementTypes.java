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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser;

import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ILazyParseableElementType;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderFactory;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionUtil;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementType;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocElementTypeImpl;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocLexer;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl.GrDocCommentImpl;

/**
 * @author ilyas
 */
public interface GroovyDocElementTypes extends GroovyDocTokenTypes {

  /**
   * GroovyDoc comment
   */
  ILazyParseableElementType GROOVY_DOC_COMMENT = new ILazyParseableElementType("GrDocComment", GroovyLanguage.INSTANCE) {
    public ASTNode parseContents(ASTNode chameleon) {
      final PsiElement parentElement = chameleon.getTreeParent().getPsi();
      final Project project = JavaPsiFacade.getInstance(parentElement.getProject()).getProject();

      LanguageVersion defaultVersion = LanguageVersionUtil.findDefaultVersion(getLanguage());

      final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, new GroovyDocLexer(), getLanguage(),
                                                                               defaultVersion, chameleon.getText());
      final PsiParser parser = new GroovyDocParser();

      return parser.parse(this, builder, defaultVersion).getFirstChildNode();
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new GrDocCommentImpl(text);
    }
  };

  GroovyDocElementType GDOC_TAG = new GroovyDocElementTypeImpl("GroovyDocTag");
  GroovyDocElementType GDOC_INLINED_TAG = new GroovyDocElementTypeImpl("GroovyDocInlinedTag");

  GroovyDocElementType GDOC_REFERENCE_ELEMENT = new GroovyDocElementTypeImpl("GroovyDocReferenceElement");
  GroovyDocElementType GDOC_PARAM_REF = new GroovyDocElementTypeImpl("GroovyDocParameterReference");
  GroovyDocElementType GDOC_METHOD_REF = new GroovyDocElementTypeImpl("GroovyDocMethodReference");
  GroovyDocElementType GDOC_FIELD_REF = new GroovyDocElementTypeImpl("GroovyDocFieldReference");
  GroovyDocElementType GDOC_METHOD_PARAMS = new GroovyDocElementTypeImpl("GroovyDocMethodParameterList");
  GroovyDocElementType GDOC_METHOD_PARAMETER = new GroovyDocElementTypeImpl("GroovyDocMethodParameter");
}
