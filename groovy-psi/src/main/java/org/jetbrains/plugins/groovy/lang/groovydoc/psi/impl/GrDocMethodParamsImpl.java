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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParameter;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class GrDocMethodParamsImpl extends GroovyDocPsiElementImpl implements GrDocMethodParams {

  private static final Logger LOG = Logger.getInstance(GrDocMethodParamsImpl.class);

  public GrDocMethodParamsImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "GrDocMethodParameterList";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocMethodParameterList(this);
  }

  public PsiType[] getParameterTypes() {
    ArrayList<PsiType> types = new ArrayList<PsiType>();
    PsiManager manager = getManager();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    for (GrDocMethodParameter parameter : getParameters()) {
      GrDocReferenceElement typeElement = parameter.getTypeElement();
      try {
        PsiType type = factory.createTypeFromText(typeElement.getText(), this);
        type = TypesUtil.boxPrimitiveType(type, manager, scope);
        types.add(type);
      }
      catch (IncorrectOperationException e) {
        LOG.info(e);
        types.add(null);
      }
    }
    return types.toArray(new PsiType[types.size()]);
  }

  public GrDocMethodParameter[] getParameters() {
    List<GrDocMethodParameter> result = new ArrayList<GrDocMethodParameter>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrDocMethodParameter) result.add((GrDocMethodParameter)cur);
    }
    return result.toArray(new GrDocMethodParameter[result.size()]);
  }

  @Nonnull
  public PsiElement getLeftParen() {
    ASTNode paren = getNode().findChildByType(GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN);
    assert paren != null;
    return paren.getPsi();
  }

  @Nullable
  public PsiElement getRightParen() {
    ASTNode paren = getNode().findChildByType(GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN);
    return paren != null ? paren.getPsi() : null;
  }

}
