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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import java.util.List;

import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtilRt;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationNameValuePairImpl extends GroovyPsiElementImpl implements GrAnnotationNameValuePair, PsiPolyVariantReference {
  public GrAnnotationNameValuePairImpl(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotationNameValuePair(this);
  }

  public String toString() {
    return "Annotation member value pair";
  }

  @Override
  @javax.annotation.Nullable
  public String getName() {
    final PsiElement nameId = getNameIdentifierGroovy();
    return nameId != null ? nameId.getText() : null;
  }

  @Override
  public String getLiteralValue() {
    return null;
  }

  @Override
  @javax.annotation.Nullable
  public PsiElement getNameIdentifierGroovy() {
    PsiElement child = getFirstChild();
    if (child == null) return null;

    IElementType type = child.getNode().getElementType();
    if (TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(type)) return child;

    return null;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public GrAnnotationMemberValue getValue() {
    return findChildByClass(GrAnnotationMemberValue.class);
  }

  @Override
  @Nonnull
  public PsiAnnotationMemberValue setValue(@Nonnull PsiAnnotationMemberValue newValue) {
    GrAnnotationMemberValue value = getValue();
    if (value == null) {
      return (PsiAnnotationMemberValue)add(newValue);
    }
    else {
      return (PsiAnnotationMemberValue)value.replace(newValue);
    }
  }

  @Override
  public PsiReference getReference() {
    return getNameIdentifierGroovy() == null ? null : this;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    PsiElement nameId = getNameIdentifierGroovy();
    assert nameId != null;
    return nameId.getTextRange().shiftRight(-getTextRange().getStartOffset());
  }

  @Override
  @javax.annotation.Nullable
  public PsiElement resolve() {
    final GroovyResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0].getElement() : null;
  }

  @Override
  @Nonnull
  public String getCanonicalText() {
    return getRangeInElement().substring(getText());
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getNameIdentifierGroovy();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null;
    if (nameElement != null) {
      ASTNode node = nameElement.getNode();
      assert node != null;
      getNode().replaceChild(node, newNameNode);
    } else {
      PsiElement first = getFirstChild();
      ASTNode anchorBefore = first != null ? first.getNode() : null;
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", anchorBefore);
      getNode().addChild(newNameNode, anchorBefore);
    }

    return this;
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("NYI");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return element instanceof PsiMethod && getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  @Nonnull
  public Object[] getVariants() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    GrAnnotation annotation = PsiImplUtil.getAnnotation(this);
    if (annotation != null) {
      GrCodeReferenceElement ref = annotation.getClassReference();
      PsiElement resolved = ref.resolve();

      String declaredName = getName();
      String name = declaredName == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : declaredName;

      if (resolved instanceof PsiClass) {
        final PsiAnnotation collector = GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved);
        if (collector != null) {
          return multiResolveFromAlias(annotation, name, collector);
        }

        if (((PsiClass)resolved).isAnnotationType()) {
          return multiResolveFromAnnotationType((PsiClass)resolved, name);
        }
      }
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private static GroovyResolveResult[] multiResolveFromAnnotationType(@Nonnull PsiClass resolved, @Nonnull String name) {
    PsiMethod[] methods = resolved.findMethodsByName(name, false);
    if (methods.length == 0) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult[] results = new GroovyResolveResult[methods.length];
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];
      results[i] = new GroovyResolveResultImpl(method, true);
    }
    return results;
  }

  private static GroovyResolveResult[] multiResolveFromAlias(@Nonnull GrAnnotation alias, @Nonnull String name, @Nonnull PsiAnnotation annotationCollector) {
    List<GroovyResolveResult> result = ContainerUtilRt.newArrayList();

    List<GrAnnotation> annotations = ContainerUtilRt.newArrayList();
    GrAnnotationCollector.collectAnnotations(annotations, alias, annotationCollector);

    for (GrAnnotation annotation : annotations) {
      final PsiElement clazz = annotation.getClassReference().resolve();
      if (clazz instanceof PsiClass && ((PsiClass)clazz).isAnnotationType()) {
        if (GroovyCommonClassNames.GROOVY_TRANSFORM_ANNOTATION_COLLECTOR.equals(((PsiClass)clazz).getQualifiedName())) continue;
        for (PsiMethod method : ((PsiClass)clazz).findMethodsByName(name, false)) {
          result.add(new GroovyResolveResultImpl(method, true));
        }
      }
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }
}
