/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary;

import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiJavaCodeReferenceElement;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrThrowsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.04.2007
 */
public class GrThrowsClauseImpl extends GroovyPsiElementImpl implements GrThrowsClause {
  public GrThrowsClauseImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitThrowsClause(this);
  }

  public String toString() {
    return "Throw clause";
  }

  @Override
  @Nonnull
  public PsiJavaCodeReferenceElement[] getReferenceElements() {
    PsiClassType[] types = getReferencedTypes();
    if (types.length == 0) return PsiJavaCodeReferenceElement.EMPTY_ARRAY;

    PsiManager manager = getManager();

    List<PsiJavaCodeReferenceElement> result = ContainerUtil.newArrayList();
    for (PsiClassType type : types) {
      PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
      PsiClass resolved = resolveResult.getElement();
      if (resolved != null) {
        result.add(new LightClassReference(manager, type.getCanonicalText(), resolved, resolveResult.getSubstitutor()));
      }
    }
    return result.toArray(new PsiJavaCodeReferenceElement[result.size()]);
  }

  @Override
  @Nonnull
  public PsiClassType[] getReferencedTypes() {
    List<GrCodeReferenceElement> refs = new ArrayList<GrCodeReferenceElement>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (cur instanceof GrCodeReferenceElement) refs.add((GrCodeReferenceElement)cur);
    }
    if (refs.isEmpty()) return PsiClassType.EMPTY_ARRAY;

    PsiClassType[] result = new PsiClassType[refs.size()];
    for (int i = 0; i < result.length; i++) {
      result[i] = new GrClassReferenceType(refs.get(i));
    }

    return result;
  }

  @Override
  public Role getRole() {
    return Role.THROWS_LIST;
  }

  @Override
  public PsiElement add(@Nonnull PsiElement element) throws IncorrectOperationException {
    if (element instanceof GrCodeReferenceElement || element instanceof PsiJavaCodeReferenceElement) {
      if (findChildByClass(GrCodeReferenceElement.class) == null) {
        getNode().addLeaf(GroovyTokenTypes.kTHROWS, "throws", null);
      }
      else {
        PsiElement lastChild = getLastChild();
        lastChild = PsiUtil.skipWhitespacesAndComments(lastChild, false);
        if (!lastChild.getNode().getElementType().equals(GroovyTokenTypes.mCOMMA)) {
          getNode().addLeaf(GroovyTokenTypes.mCOMMA, ",", null);
        }
      }

      if (element instanceof PsiJavaCodeReferenceElement) {
        element = GroovyPsiElementFactory.getInstance(getProject()).createCodeReferenceElementFromText(element.getText());
      }
    }
    return super.add(element);
  }

}
