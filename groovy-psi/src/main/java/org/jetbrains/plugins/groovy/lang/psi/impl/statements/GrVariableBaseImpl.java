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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.application.util.RecursionManager;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenType;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Supplier;

/**
 * @author ilyas
 */
public abstract class GrVariableBaseImpl<T extends StubElement> extends GrStubElementBase<T> implements GrVariable {
  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableImpl");

  public GrVariableBaseImpl(ASTNode node) {
    super(node);
  }

  protected GrVariableBaseImpl(final T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  @Override
  public PsiElement getParent() {
    return getParentByTree();
  }

  @Override
  @Nullable
  public PsiTypeElement getTypeElement() {
    return PsiImplUtil.getOrCreateTypeElement(getTypeElementGroovy());
  }

  @Override
  @Nullable
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException
  {
  }

  @Override
  @Nullable
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiElement parent = getParent();
    PsiElement prev = PsiUtil.getPrevNonSpace(this);
    PsiElement next = PsiUtil.getNextNonSpace(this);
    ASTNode parentNode = parent.getNode();
    assert parentNode != null;
    super.delete();
    if (prev != null && prev.getNode() != null && prev.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      prev.delete();
    } else if (next instanceof LeafPsiElement && next.getNode() != null && next.getNode().getElementType() == GroovyTokenTypes.mCOMMA) {
      next.delete();
    }
    if (parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getVariables().length == 0) {
      parent.delete();
    }
  }


  @Override
  public boolean hasModifierProperty(@NonNls @Nonnull String property) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(property);
  }

  @Override
  @Nonnull
  public PsiType getType() {
    PsiType type = getDeclaredType();
    return type != null ? type : TypesUtil.getJavaLangObject(this);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return ((GrVariableDeclaration)parent).getTypeElementGroovyForVariable(this);
    }
    return null;
  }

  @Override
  @Nullable
  public PsiType getDeclaredType() {
    GrTypeElement typeElement = getTypeElementGroovy();
    if (typeElement != null) return typeElement.getType();

    return null;
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    final GrExpression initializer = getInitializerGroovy();

    GrTypeElement typeElement = getTypeElementGroovy();
    PsiType declaredType = null;
    if (typeElement != null) {
      declaredType = typeElement.getType();
      if (!(declaredType instanceof PsiClassType)) {
        return declaredType;
      }
    }

    if (initializer != null) {
      PsiType initializerType = RecursionManager.doPreventingRecursion(this, true, new Supplier<PsiType>() {
        @Override
        public PsiType get() {
          return initializer.getType();
        }
      });
      if (declaredType == null) return initializerType;

      if (initializerType instanceof PsiClassType && TypesUtil.isAssignable(declaredType, initializerType, this)) {
        final PsiClassType.ClassResolveResult initializerResult = ((PsiClassType)initializerType).resolveGenerics();
        final PsiClass initializerClass = initializerResult.getElement();
        if (initializerClass != null &&
            !com.intellij.java.language.psi.util.PsiUtil.isRawSubstitutor(initializerClass, initializerResult.getSubstitutor())) {
          return initializerType;
        }
      }

    }


    return declaredType;
  }

  @Override
  public void setType(@Nullable PsiType type) {
    final GrVariableDeclaration variableDeclaration = getDeclaration();
    if (variableDeclaration == null) return;
    final GrTypeElement typeElement = variableDeclaration.getTypeElementGroovyForVariable(this);

    if (type == null) {
      if (typeElement != null) {
        if (!variableDeclaration.isTuple() && variableDeclaration.getModifierList().getModifiers().length == 0) {
          variableDeclaration.getModifierList().setModifierProperty(GrModifier.DEF, true);
        }
        typeElement.delete();
      }
      return;
    }
    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    GrTypeElement newTypeElement;
    try {
      newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }

    if (typeElement == null) {
      newTypeElement = (GrTypeElement)getParent().addBefore(newTypeElement, this);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  @Nonnull
  public PsiElement getNameIdentifierGroovy() {
    PsiElement ident = findChildByType(TokenSets.PROPERTY_NAMES);
    assert ident != null : getText();
    return ident;
  }

  @Override
  @Nullable
  public GrExpression getInitializerGroovy() {
    final PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).isTuple()){
      final GrVariableDeclaration tuple = (GrVariableDeclaration)parent;
      final GrExpression initializer = tuple.getTupleInitializer();

      if (initializer instanceof GrListOrMap){
        final GrListOrMap listOrMap = (GrListOrMap)initializer;
        final GrExpression[] initializers = listOrMap.getInitializers();

        final int varNumber = ArrayUtil.indexOf(tuple.getVariables(), this);
        if (initializers.length < varNumber + 1) return null;

        return initializers[varNumber];
      }
    }
    return GroovyPsiElementImpl.findExpressionChild(this);
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifierGroovy().getTextRange().getStartOffset();
  }

  @Override
  public PsiElement setName(@NonNls @Nonnull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(name, getNameIdentifierGroovy());
    return this;
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    final GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(this, GrVariableDeclarationOwner.class);
    if (owner != null) return new LocalSearchScope(owner);
    return super.getUseScope();
  }

  @Override
  @Nonnull
  public String getName() {
    return PsiImplUtil.getName(this);
  }

  @Override
  @Nonnull
  public PsiIdentifier getNameIdentifier() {
    return PsiUtil.getJavaNameIdentifier(this);
  }

  @Override
  @Nullable
  public GrModifierList getModifierList() {
    final GrVariableDeclaration variableDeclaration = getDeclaration();
    if (variableDeclaration!=null) return variableDeclaration.getModifierList();
    return null;
  }

  @Nullable
  private GrVariableDeclaration getDeclaration() {
    PsiElement parent = getParent();
    if (parent instanceof GrVariableDeclaration) {
      return (GrVariableDeclaration)parent;
    }
    return null;
  }

  @Override
  public void deleteChildInternal(@Nonnull ASTNode child) {
    final PsiElement psi = child.getPsi();
    if (psi == getInitializerGroovy()) {
      deleteChildInternal(findNotNullChildByType(GroovyTokenTypes.mASSIGN).getNode());
    }
    super.deleteChildInternal(child);
  }

  @Override
  public void setInitializerGroovy(GrExpression initializer) {
    if (getParent() instanceof GrVariableDeclaration && ((GrVariableDeclaration)getParent()).isTuple()) {
      throw new UnsupportedOperationException("don't invoke 'setInitializer()' for tuple declaration");
    }

    GrExpression oldInitializer = getInitializerGroovy();
    if (initializer == null) {
      if (oldInitializer != null) {
        oldInitializer.delete();
        PsiElement assign = findChildByType(GroovyTokenTypes.mASSIGN);
        if (assign != null) {
          assign.delete();
        }
      }
      return;
    }


    if (oldInitializer != null) {
      oldInitializer.replaceWithExpression(initializer, true);
    }
    else {
      getNode().addLeaf(TokenType.WHITE_SPACE, " ", null);
      getNode().addLeaf(GroovyTokenTypes.mASSIGN, "=", null);
      addAfter(initializer, getLastChild());
    }
  }
}
