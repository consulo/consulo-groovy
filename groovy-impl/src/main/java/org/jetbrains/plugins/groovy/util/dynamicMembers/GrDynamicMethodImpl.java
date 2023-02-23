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
package org.jetbrains.plugins.groovy.util.dynamicMembers;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */

public class GrDynamicMethodImpl extends LightElement implements GrMethod {
  protected final GrMethod myMethod;

  public GrDynamicMethodImpl(GrMethod method) {
    super(method.getManager(), method.getLanguage());
    myMethod = method;
  }

  public PsiClass getContainingClass() {
    return null;
  }

  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  public GrParameter[] getParameters() {
    return myMethod.getParameters();
  }

  @Override
  public String toString() {
    return "grails dynamic method";
  }

  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @Nonnull
  public PsiMethod[] findSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @Nonnull
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  public PsiElement copy() {
    return myMethod.copy();
  }

  public GrMember[] getMembers() {
    return new GrMember[0];
  }

  @Nonnull
  public GrModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  public boolean hasModifierProperty(@NonNls @Nonnull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @Nonnull
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myMethod.getNamedParameters();
  }

  @Nonnull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return GrReflectedMethod.EMPTY_ARRAY;
  }

  public GrOpenBlock getBlock() {
    return null;
  }

  public void setBlock(GrCodeBlock newBlock) {

  }

  public GrTypeElement getReturnTypeElementGroovy() {
    return myMethod.getReturnTypeElementGroovy();
  }

  public PsiType getInferredReturnType() {
    return myMethod.getInferredReturnType();
  }

  @Nonnull
  public String getName() {
    return myMethod.getName();
  }

  @Nonnull
  public GrParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @Nonnull
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isConstructor() {
    return false;
  }

  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @Nonnull
  public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  @Nonnull
  public PsiElement getNameIdentifierGroovy() {
    return myMethod.getNameIdentifierGroovy();
  }

  public void accept(GroovyElementVisitor visitor) {
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  public GrDocComment getDocComment() {
    return null;
  }

  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    return this;
  }

  @Nonnull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return myMethod.getHierarchicalMethodSignature();
  }

  public PsiType getReturnTypeNoResolve() {
    return myMethod.getReturnType();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return another instanceof GrDynamicMethodImpl && myMethod.isEquivalentTo(((GrDynamicMethodImpl)another).myMethod);
  }

  public GrTypeElement setReturnType(PsiType newReturnType) {
    throw new UnsupportedOperationException("Dynamic method can't change it's return type");
  }
}
