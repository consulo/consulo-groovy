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
package org.jetbrains.plugins.groovy.impl.util.dynamicMembers;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.MethodSignatureBackedByPsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */

public class GrDynamicMethodImpl extends LightElement implements GrMethod {
  protected final GrMethod myMethod;

  @RequiredReadAction
  public GrDynamicMethodImpl(GrMethod method) {
    super(method.getManager(), method.getLanguage());
    myMethod = method;
  }

  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  public PsiType getReturnType() {
    return myMethod.getReturnType();
  }

  @Override
  public PsiTypeElement getReturnTypeElement() {
    return myMethod.getReturnTypeElement();
  }

  @Override
  public GrParameter[] getParameters() {
    return myMethod.getParameters();
  }

  @Override
  public String toString() {
    return "grails dynamic method";
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myMethod.getNameIdentifier();
  }

  @Nonnull
  @Override
  public PsiMethod[] findSuperMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return Collections.emptyList();
  }

  @Override
  public PsiMethod findDeepestSuperMethod() {
    return null;
  }

  @Nonnull
  @Override
  public PsiMethod[] findDeepestSuperMethods() {
    return new PsiMethod[0];
  }

  @Override
  public PsiElement copy() {
    return myMethod.copy();
  }

  @Override
  public GrMember[] getMembers() {
    return new GrMember[0];
  }

  @Nonnull
  @Override
  public GrModifierList getModifierList() {
    return myMethod.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@Nonnull String name) {
    return myMethod.hasModifierProperty(name);
  }

  @Nonnull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return myMethod.getNamedParameters();
  }

  @Nonnull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return GrReflectedMethod.EMPTY_ARRAY;
  }

  @Override
  public GrOpenBlock getBlock() {
    return null;
  }

  @Override
  public void setBlock(GrCodeBlock newBlock) {
  }

  @Override
  public GrTypeElement getReturnTypeElementGroovy() {
    return myMethod.getReturnTypeElementGroovy();
  }

  @Override
  public PsiType getInferredReturnType() {
    return myMethod.getInferredReturnType();
  }

  @Nonnull
  @Override
  public String getName() {
    return myMethod.getName();
  }

  @Nonnull
  @Override
  public GrParameterList getParameterList() {
    return myMethod.getParameterList();
  }

  @Nonnull
  @Override
  public PsiReferenceList getThrowsList() {
    return myMethod.getThrowsList();
  }

  @Override
  public PsiCodeBlock getBody() {
    return null;
  }

  @Override
  public boolean isConstructor() {
    return false;
  }

  @Override
  public boolean isVarArgs() {
    return myMethod.isVarArgs();
  }

  @Nonnull
  @Override
  public MethodSignature getSignature(@Nonnull PsiSubstitutor substitutor) {
    return myMethod.getSignature(substitutor);
  }

  @Nonnull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return myMethod.getNameIdentifierGroovy();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return myMethod.isDeprecated();
  }

  @Override
  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @Nonnull
  @Override
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    return this;
  }

  @Nonnull
  @Override
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

  @Override
  public GrTypeElement setReturnType(PsiType newReturnType) {
    throw new UnsupportedOperationException("Dynamic method can't change it's return type");
  }
}
