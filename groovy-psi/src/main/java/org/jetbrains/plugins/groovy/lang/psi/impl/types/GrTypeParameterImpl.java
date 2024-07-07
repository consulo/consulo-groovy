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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.java.language.impl.psi.impl.InheritanceImplUtil;
import com.intellij.java.language.psi.*;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiReference;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeParameterStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public class GrTypeParameterImpl extends GrStubElementBase<GrTypeParameterStub> implements GrTypeParameter,
  StubBasedPsiElement<GrTypeParameterStub> {

  public GrTypeParameterImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrTypeParameterImpl(GrTypeParameterStub stub) {
    super(stub, GroovyElementTypes.TYPE_PARAMETER);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  @Nullable
  public GrTypeDefinitionBody getBody() {
    return null;
  }

  @Override
  @Nonnull
  public GrMembersDeclaration[] getMemberDeclarations() {
    return GrMembersDeclaration.EMPTY_ARRAY;
  }

  @Override
  public GrExtendsClause getExtendsClause() {
    return null;
  }

  @Override
  public GrImplementsClause getImplementsClause() {
    return null;
  }

  @Override
  public String[] getSuperClassNames() {
    final PsiReference[] types = getExtendsList().getReferences();
    List<String> names = new ArrayList<String>(types.length);
    for (PsiReference type : types) {
      names.add(type.getCanonicalText());
    }
    return ArrayUtil.toStringArray(names);
  }

  @Override
  @Nonnull
  public GrMethod[] getCodeMethods() {
    return GrMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  public String toString() {
    return "Type parameter";
  }

  @Override
  @Nullable
  @NonNls
  public String getQualifiedName() {
    return null;
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @Override
  public boolean isAnonymous() {
    return false;
  }

  @Override
  public boolean isAnnotationType() {
    return false;
  }

  @Override
  public boolean isEnum() {
    return false;
  }

  @Override
  public boolean isTrait() {
    return false;
  }

  @Override
  @Nonnull
  public PsiReferenceList getExtendsList() {
    final GrTypeParameterParameterExtendsListImpl list = findChildByClass(GrTypeParameterParameterExtendsListImpl
                                                                            .class);
    assert list != null;
    return list;
  }

  @Override
  @Nullable
  public PsiReferenceList getImplementsList() {
    return null;
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return getExtendsList().getReferencedTypes();
  }

  @Override
  @Nonnull
  public PsiClassType[] getImplementsListTypes() {
    return new PsiClassType[0];
  }

  @Override
  @Nullable
  public PsiClass getSuperClass() {
    return GrClassImplUtil.getSuperClass(this);
  }

  @Override
  public PsiClass[] getInterfaces() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getSupers() {
    return GrClassImplUtil.getSupers(this);
  }

  @Override
  @Nonnull
  public PsiClassType[] getSuperTypes() {
    return GrClassImplUtil.getSuperTypes(this);
  }

  @Override
  @Nonnull
  public GrField[] getFields() {
    return GrField.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public GrField[] getCodeFields() {
    return GrField.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public GrMethod[] getCodeConstructors() {
    return GrMethod.EMPTY_ARRAY;
  }

  @Override
  public PsiField findCodeFieldByName(String name, boolean checkBases) {
    return null;
  }

  @Override
  @Nonnull
  public PsiMethod[] getMethods() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiMethod[] getConstructors() {
    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiClass[] getInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public GrClassInitializer[] getInitializers() {
    return GrClassInitializer.EMPTY_ARRAY;
  }

  @Override
  @Nonnull
  public PsiField[] getAllFields() {
    return GrClassImplUtil.getAllFields(this);
  }

  @Override
  @Nonnull
  public PsiMethod[] getAllMethods() {
    return GrClassImplUtil.getAllMethods(this);
  }

  @Override
  @Nonnull
  public PsiClass[] getAllInnerClasses() {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  @Nullable
  public PsiField findFieldByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findFieldByName(this, name, checkBases, true);
  }

  @Override
  @Nullable
  public PsiMethod findMethodBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findMethodBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsBySignature(PsiMethod patternMethod, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsBySignature(this, patternMethod, checkBases);
  }

  @Override
  @Nonnull
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    return GrClassImplUtil.findCodeMethodsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(@NonNls String name,
                                                                                     boolean checkBases) {
    return GrClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases);
  }

  @Override
  @Nonnull
  public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
    return GrClassImplUtil.getAllMethodsAndTheirSubstitutors(this);
  }

  @Override
  @Nullable
  public PsiClass findInnerClassByName(@NonNls String name, boolean checkBases) {
    return null;
  }

  @Override
  @Nullable
  public PsiJavaToken getLBrace() {
    return null;
  }

  @Override
  @Nullable
  public PsiJavaToken getRBrace() {
    return null;
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  @Nullable
  public PsiElement getScope() {
    return null;
  }

  @Override
  public boolean isInheritor(@Nonnull PsiClass baseClass, boolean checkDeep) {
    return InheritanceImplUtil.isInheritor(this, baseClass, checkDeep);
  }

  @Override
  public boolean isInheritorDeep(PsiClass baseClass, @Nullable PsiClass classToByPass) {
    return InheritanceImplUtil.isInheritorDeep(this, baseClass, classToByPass);
  }

  @Override
  @Nullable
  public PsiClass getContainingClass() {
    return null;
  }

  @Override
  @Nonnull
  public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
    return Collections.emptyList(); //todo
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    final PsiElement parent = getParent();
    if (parent == null) {
      throw new PsiInvalidElementAccessException(this);
    }
    final PsiElement parentParent = parent.getParent();
    if (!(parentParent instanceof PsiTypeParameterListOwner)) {
      return null;
    }
    return (PsiTypeParameterListOwner)parentParent;
  }

  @Override
  public int getIndex() {
    final GrTypeParameterList list = (GrTypeParameterList)getParent();
    return list.getTypeParameterIndex(this);
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @Override
  @Nonnull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement result = findChildByType(GroovyTokenTypes.mIDENT);
    assert result != null;
    return result;
  }

  @Override
  @Nullable
  public GrModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull String name) {
    return false;
  }

  @Override
  @Nullable
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasTypeParameters() {
    return false;
  }

  @Override
  @Nullable
  public GrTypeParameterList getTypeParameterList() {
    return null;
  }

  @Override
  @Nonnull
  public PsiTypeParameter[] getTypeParameters() {
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @Override
  public String getName() {
    final GrTypeParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifierGroovy().getText();
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    return GrClassImplUtil.processDeclarations(this, processor, state, lastParent, place);
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation findAnnotation(@Nonnull @NonNls String qualifiedName) {
    return null;
  }

  @Override
  @Nonnull
  public PsiAnnotation addAnnotation(@Nonnull @NonNls String qualifiedName) {
    throw new IncorrectOperationException();
  }

  @Override
  @Nonnull
  public PsiAnnotation[] getApplicableAnnotations() {
    return PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitTypeParameter(this);
  }
}
