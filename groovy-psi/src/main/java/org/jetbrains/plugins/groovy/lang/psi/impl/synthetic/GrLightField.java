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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiType;
import consulo.application.util.CachedValueProvider;
import consulo.content.scope.SearchScope;
import consulo.language.impl.psi.ResolveScopeManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * @author sergey.evdokimov
 */
public class GrLightField extends GrLightVariable implements GrField {

  private final PsiClass myContainingClass;

  public GrLightField(@Nonnull PsiClass containingClass,
                      @NonNls String name,
                      @Nonnull PsiType type,
                      @Nonnull PsiElement navigationElement) {
    super(containingClass.getManager(), name, type, navigationElement);
    myContainingClass = containingClass;
  }

  public GrLightField(@Nonnull PsiClass containingClass,
                      @NonNls String name,
                      @Nonnull String type) {
    super(containingClass.getManager(), name, type, containingClass);
    myContainingClass = containingClass;
    setNavigationElement(this);
  }

  @Override
  public GrDocComment getDocComment() {
    return null;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Nonnull
  @Override
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  @Override
  public PsiClass getContainingClass() {
    return myContainingClass;
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isProperty() {
    return PsiUtil.isProperty(this);
  }

  @Nullable
  @Override
  public GrAccessorMethod getSetter() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<GrAccessorMethod>() {
      @Nullable
      @Override
      public Result<GrAccessorMethod> compute() {
        return Result.create(GrAccessorMethodImpl.createSetterMethod(GrLightField.this),
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  @Nonnull
  @Override
  public GrAccessorMethod[] getGetters() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<GrAccessorMethod[]>() {
      @Nullable
      @Override
      public Result<GrAccessorMethod[]> compute() {
        return Result.create(GrAccessorMethodImpl.createGetterMethods(GrLightField.this),
                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    });
  }

  @Nonnull
  @Override
  public Map<String, NamedArgumentDescriptor> getNamedParameters() {
    return Collections.emptyMap();
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    throw new IncorrectOperationException("cannot set initializer to light field!");
  }

  @Override
  public GrExpression getInitializerGroovy() {
    return null;
  }

  @Override
  public void setType(@Nullable PsiType type) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public GrTypeElement getTypeElementGroovy() {
    return null;
  }

  @Override
  public PsiType getTypeGroovy() {
    return getType();
  }

  @Override
  public PsiType getDeclaredType() {
    return getType();
  }

  @Nonnull
  @Override
  public PsiElement getNameIdentifierGroovy() {
    return myNameIdentifier;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitField(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {

  }

  @Override
  public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
    PsiElement res = super.setName(name);
    return res;
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (super.isEquivalentTo(another)) return true;

    if (another instanceof GrLightField) {
      GrLightField otherField = (GrLightField)another;
      return otherField.myContainingClass == myContainingClass && getName().equals(otherField.getName());
    }

    return false;
  }

  @Override
  public PsiElement copy() {
    assert getNavigationElement() != this;
    GrLightField copy = new GrLightField(myContainingClass, getName(), getType(), getNavigationElement());
    copy.setCreatorKey(getCreatorKey());
    return copy;
  }
}
