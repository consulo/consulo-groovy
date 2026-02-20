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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import com.intellij.java.language.psi.*;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrFieldImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantImpl extends GrFieldImpl implements GrEnumConstant {
  private final MyReference myReference = new MyReference();

  public GrEnumConstantImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrEnumConstantImpl(GrFieldStub stub) {
    super(stub, GroovyElementTypes.ENUM_CONSTANT);
  }

  public String toString() {
    return "Enumeration constant";
  }

  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull String property) {
    if (property.equals(PsiModifier.STATIC)) return true;
    if (property.equals(PsiModifier.PUBLIC)) return true;
    if (property.equals(PsiModifier.FINAL)) return true;
    return false;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumConstant(this);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  @Nonnull
  public PsiType getType() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory().createType(getContainingClass(), PsiSubstitutor.EMPTY);
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    return getType();
  }

  @Override
  public void setType(@Nullable PsiType type) {
    throw new RuntimeException("Cannot set type for enum constant");
  }

  @Override
  @Nullable
  public GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public boolean isProperty() {
    return false;
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    GroovyResolveResult result = new GroovyResolveResultImpl(getContainingClass(), this, null, PsiSubstitutor.EMPTY, true, true);
    return new GroovyResolveResult[]{result};
  }

  @Override
  @Nullable
  public GrArgumentList getArgumentList() {
    return findChildByClass(GrArgumentList.class);
  }

  @Override
  public GrNamedArgument addNamedArgument(GrNamedArgument namedArgument) throws IncorrectOperationException {
    GrArgumentList list = getArgumentList();
    assert list != null;
    if (list.getText().trim().isEmpty()) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      GrArgumentList newList = factory.createArgumentList();
      list = (GrArgumentList)list.replace(newList);
    }
    return list.addNamedArgument(namedArgument);
  }

  @Nonnull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrNamedArgument.EMPTY_ARRAY : argumentList.getNamedArguments();
  }

  @Nonnull
  @Override
  public GrExpression[] getExpressionArguments() {
    GrArgumentList argumentList = getArgumentList();
    return argumentList == null ? GrExpression.EMPTY_ARRAY : argumentList.getExpressionArguments();
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
    return multiResolveGroovy(true);
  }

  @Nonnull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Override
  public PsiMethod resolveMethod() {
    return PsiImplUtil.extractUniqueElement(multiResolveGroovy(false));
  }

  @Nonnull
  @Override
  public JavaResolveResult resolveMethodGenerics() {
    return JavaResolveResult.EMPTY;
  }

  @Override
  @Nullable
  public GrEnumConstantInitializer getInitializingClass() {
    return findChildByClass(GrEnumConstantInitializer.class);
  }

  @Nonnull
  @Override
  public PsiEnumConstantInitializer getOrCreateInitializingClass() {
    GrEnumConstantInitializer initializingClass = getInitializingClass();
    if (initializingClass != null) return initializingClass;

    GrEnumConstantInitializer initializer =
      GroovyPsiElementFactory.getInstance(getProject()).createEnumConstantFromText("foo{}").getInitializingClass();
    LOG.assertTrue(initializer != null);
    GrArgumentList argumentList = getArgumentList();
    if (argumentList != null) {
      return (PsiEnumConstantInitializer)addAfter(initializer, argumentList);
    }
    else {
      return (PsiEnumConstantInitializer)addAfter(initializer, getNameIdentifierGroovy());
    }
  }

  @Override
  public PsiReference getReference() {
    return myReference;
  }

  @Nonnull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolveGroovy(false));
  }

  @Override
  public PsiMethod resolveConstructor() {
    return resolveMethod();
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolveGroovy(boolean incompleteCode) {
    PsiType[] argTypes = PsiUtil.getArgumentTypes(getFirstChild(), false);
    PsiClass clazz = getContainingClass();
    return ResolveUtil.getAllClassConstructors(clazz, PsiSubstitutor.EMPTY, argTypes, this);
  }

  @Nonnull
  @Override
  public PsiClass getContainingClass() {
    PsiClass aClass = super.getContainingClass();
    assert aClass != null;
    return aClass;
  }

  private class MyReference implements PsiPolyVariantReference {
    @Override
    @Nonnull
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      return GrEnumConstantImpl.this.multiResolve(false);
    }

    @Override
    public PsiElement getElement() {
      return GrEnumConstantImpl.this;
    }

    @Override
    public TextRange getRangeInElement() {
      return getNameIdentifierGroovy().getTextRange().shiftRight(-getTextOffset());
    }

    @Override
    public PsiElement resolve() {
      return resolveMethod();
    }

    @Nonnull
    public GroovyResolveResult advancedResolve() {
      return GrEnumConstantImpl.this.advancedResolve();
    }

    @Override
    @Nonnull
    public String getCanonicalText() {
      return getContainingClass().getName();
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Override
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException
	{
      throw new IncorrectOperationException("invalid operation");
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element instanceof GrMethod && ((GrMethod)element).isConstructor() && getManager().areElementsEquivalent(resolve(), element);
    }

    @Override
    @Nonnull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
      return false;
    }
  }
}
