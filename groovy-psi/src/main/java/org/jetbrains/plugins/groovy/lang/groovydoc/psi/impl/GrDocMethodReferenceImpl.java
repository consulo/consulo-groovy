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

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import javax.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodParams;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMethodReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ilyas
 */
public class GrDocMethodReferenceImpl extends GrDocMemberReferenceImpl implements GrDocMethodReference {

  public GrDocMethodReferenceImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "GrDocMethodReference";
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDocMethodReference(this);
  }

  @Nonnull
  public GrDocMethodParams getParameterList() {
    GrDocMethodParams child = findChildByClass(GrDocMethodParams.class);
    assert child != null;
    return child;
  }

  @Override
  public PsiElement bindToText(Project project, String text) {
    GrDocComment comment = GroovyPsiElementFactory.getInstance(project).createDocCommentFromText(text);
    PsiElement tag = PsiTreeUtil.getChildOfType(comment, GrDocTag.class);
    PsiElement ref = PsiTreeUtil.getChildOfType(tag, GrDocMethodReference.class);
    assert ref != null : text;
    return replace(ref);
  }

  @Override
  public PsiElement resolve() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = PsiUtil.getContextClass(this);
    }
    if (resolved instanceof PsiClass) {
      PsiType[] parameterTypes = getParameterList().getParameterTypes();
      PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass)resolved, PsiSubstitutor.EMPTY);

      MethodResolverProcessor processor = new MethodResolverProcessor(name, this, false, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(processor, ResolveState.initial(), resolved, this);
      if (processor.hasApplicableCandidates()) {
        return processor.getCandidates()[0].getElement();
      }

      MethodResolverProcessor constructorProcessor =
        new MethodResolverProcessor(name, this, true, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(constructorProcessor, ResolveState.initial(), resolved, this);
      if (constructorProcessor.hasApplicableCandidates()) {
        return constructorProcessor.getCandidates()[0].getElement();
      }
    }
    return null;
  }

  protected ResolveResult[] multiResolveImpl() {
    String name = getReferenceName();
    GrDocReferenceElement holder = getReferenceHolder();
    PsiElement resolved;
    if (holder != null) {
      GrCodeReferenceElement referenceElement = holder.getReferenceElement();
      resolved = referenceElement != null ? referenceElement.resolve() : null;
    } else {
      resolved = PsiUtil.getContextClass(this);
    }
    if (resolved instanceof PsiClass) {
      PsiType[] parameterTypes = getParameterList().getParameterTypes();
      PsiType thisType = JavaPsiFacade.getInstance(getProject()).getElementFactory().createType((PsiClass) resolved, PsiSubstitutor.EMPTY);
      MethodResolverProcessor processor = new MethodResolverProcessor(name, this, false, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      MethodResolverProcessor constructorProcessor = new MethodResolverProcessor(name, this, true, thisType, parameterTypes, PsiType.EMPTY_ARRAY);
      resolved.processDeclarations(processor, ResolveState.initial(), resolved, this);
      resolved.processDeclarations(constructorProcessor, ResolveState.initial(), resolved, this);
      return ArrayUtil.mergeArrays(processor.getCandidates(), constructorProcessor.getCandidates());
    }
    return new ResolveResult[0];
  }

  public boolean isReferenceTo(PsiElement element) {
    if (element instanceof PsiNamedElement && Comparing.equal(((PsiNamedElement) element).getName(), getReferenceName())) {
      return getManager().areElementsEquivalent(element, resolve());
    }
    return false;
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) resolved;
      final String oldName = getReferenceName();
      if (!method.getName().equals(oldName)) { //was property reference to accessor
        if (PropertyUtil.isSimplePropertyAccessor(method)) {
          final String newPropertyName = PropertyUtil.getPropertyName(newElementName);
          if (newPropertyName != null) {
            return super.handleElementRename(newPropertyName);
          }
        }
      }
    }
    return super.handleElementRename(newElementName);
  }

}
