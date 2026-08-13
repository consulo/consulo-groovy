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

import com.intellij.java.language.codeInsight.AnnotationTargetUtil;
import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import com.intellij.java.language.impl.psi.impl.light.LightClassReference;
import com.intellij.java.language.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.function.BiFunction;

/**
 * @author Dmitry.Krasilschikov
 * @since 2007-04-04
 */
public class GrAnnotationImpl extends GrStubElementBase<GrAnnotationStub> implements GrAnnotation, StubBasedPsiElement<GrAnnotationStub> {
  private static final BiFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR =
    (project, text) -> GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(text);

  public GrAnnotationImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrAnnotationImpl(GrAnnotationStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION);
  }

  @Override
  @RequiredReadAction
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @Override
  public String toString() {
    return "Annotation";
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public GrAnnotationArgumentList getParameterList() {
    return findNotNullChildByClass(GrAnnotationArgumentList.class);
  }

  @Override
  @Nullable
  @RequiredReadAction
  public String getQualifiedName() {
    GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getQualifiedName();
    }

    GrCodeReferenceElement nameRef = getClassReference();
    PsiElement resolved = nameRef.resolve();
    if (resolved instanceof PsiClass psiClass) return psiClass.getQualifiedName();
    return null;
  }

  @Override
  @Nullable
  @RequiredReadAction
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    GroovyResolveResult resolveResult = resolveWithStub();

    PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiClass)) return null;

    return new LightClassReference(getManager(), getClassReference().getText(), (PsiClass)resolved, resolveResult.getSubstitutor());
  }

  @Nonnull
  @RequiredReadAction
  private GroovyResolveResult resolveWithStub() {
    GrAnnotationStub stub = getStub();
    GrCodeReferenceElement reference = stub != null ? stub.getPsiElement().getClassReference() : getClassReference();
    return reference.advancedResolve();
  }

  @Override
  @Nullable
  @RequiredReadAction
  public PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName) {
    GrAnnotationStub stub = getStub();
    if (stub != null) {
      GrAnnotation stubbedPsi = stub.getPsiElement();
      PsiAnnotationMemberValue value = PsiImplUtil.findAttributeValue(stubbedPsi, attributeName);
      if (value == null || !PsiTreeUtil.isAncestor(stubbedPsi, value, true)) {         // if value is a default value we can use it
        return value;
      }
    }
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(String attributeName) {
    GrAnnotationStub stub = getStub();
    if (stub != null) {
      GrAnnotation stubbedPsi = stub.getPsiElement();
      PsiAnnotationMemberValue value = PsiImplUtil.findDeclaredAttributeValue(stubbedPsi, attributeName);
      if (value == null) {
        return null;
      }
    }
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  @RequiredReadAction
  @SuppressWarnings("unchecked")
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable String attributeName, T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public GrCodeReferenceElement getClassReference() {
    GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getClassReference();
    }

    return findNotNullChildByClass(GrCodeReferenceElement.class);
  }

  @Override
  @Nonnull
  @RequiredReadAction
  public String getShortName() {
    GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getShortName();
    }

    String referenceName = getClassReference().getReferenceName();
    assert referenceName != null;
    return referenceName;
  }

  @Override
  @Nullable
  @RequiredReadAction
  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    return parent instanceof PsiAnnotationOwner ? (PsiAnnotationOwner)parent : null;
  }

  @Nonnull
  public static TargetType[] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)owner;
      if (aClass.isAnnotationType()) {
        return new TargetType[]{TargetType.ANNOTATION_TYPE, TargetType.TYPE};
      }
      else if (aClass instanceof GrTypeParameter) {
        return new TargetType[]{TargetType.TYPE_PARAMETER};
      }
      else {
        return new TargetType[]{TargetType.TYPE};
      }
    }
    if (owner instanceof GrMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return new TargetType[]{TargetType.CONSTRUCTOR};
      }
      else {
        return new TargetType[]{TargetType.METHOD};
      }
    }
    if (owner instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration)owner).getVariables();
      if (variables.length == 0) {
        return TargetType.EMPTY_ARRAY;
      }
      if (variables[0] instanceof GrField || ResolveUtil.isScriptField(variables[0])) {
        return new TargetType[]{TargetType.FIELD};
      }
      else {
        return new TargetType[]{TargetType.LOCAL_VARIABLE};
      }
    }
    if (owner instanceof GrParameter) {
      return new TargetType[]{TargetType.PARAMETER};
    }
    if (owner instanceof GrPackageDefinition) {
      return new TargetType[]{TargetType.PACKAGE};
    }
    if (owner instanceof GrTypeElement) {
      return new TargetType[]{TargetType.TYPE_USE};
    }

    return TargetType.EMPTY_ARRAY;
  }

  @RequiredReadAction
  public static boolean isAnnotationApplicableTo(GrAnnotation annotation, @Nonnull TargetType... elementTypeFields) {
    return elementTypeFields.length == 0 || AnnotationTargetUtil.findAnnotationTarget(annotation, elementTypeFields) != null;
  }
}
