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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.params;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiEllipsisType;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrVariableBaseImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrParameterStub;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.ClosureParameterEnhancer;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author: Dmitry.Krasilschikov
 */
public class GrParameterImpl extends GrVariableBaseImpl<GrParameterStub> implements GrParameter, StubBasedPsiElement<GrParameterStub> {
  public GrParameterImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrParameterImpl(GrParameterStub stub) {
    super(stub, GroovyElementTypes.PARAMETER);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitParameter(this);
  }

  public String toString() {
    return "Parameter";
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    final PsiType declaredType = getDeclaredType();
    if (declaredType != null) return declaredType;

    if (isVarArgs()) {
      PsiClassType type = TypesUtil.getJavaLangObject(this);
      return new PsiEllipsisType(type);
    }

    PsiElement parent = getParent();
    if (parent instanceof GrForInClause) {
      GrExpression iteratedExpression = ((GrForInClause)parent).getIteratedExpression();
      if (iteratedExpression == null) return null;
      PsiType result = ClosureParameterEnhancer.findTypeForIteration(iteratedExpression, this);
      if (result != null) {
        return result;
      }
    }
    else if (parent instanceof GrTraditionalForClause) {
      return super.getTypeGroovy();
    }
    else if (parent instanceof GrCatchClause) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_EXCEPTION, this);
    }

    return GrVariableEnhancer.getEnhancedType(this);
  }


  @Override
  public PsiType getDeclaredType() {
    PsiType type = super.getDeclaredType();
    if (isVarArgs()) {
      if (type == null) type = TypesUtil.getJavaLangObject(this);
      return new PsiEllipsisType(type);
    }
    return type;
  }

  @Override
  @Nonnull
  public PsiType getType() {
    PsiType type = super.getType();
    if (isMainMethodFirstUntypedParameter()) {
      return TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_LANG_STRING, this).createArrayType();
    }
    return type;
  }

  private boolean isMainMethodFirstUntypedParameter() {
    if (getTypeElementGroovy() != null) return false;
    if (!(getParent() instanceof GrParameterList)) return false;
    if (getInitializerGroovy() != null) return false;

    GrParameterList parameterList = (GrParameterList)getParent();
    if (!(parameterList.getParent() instanceof GrMethod)) return false;

    GrMethod method = (GrMethod)parameterList.getParent();
    return PsiImplUtil.isMainMethod(method);
  }

  @Override
  public void setType(@Nullable PsiType type) {
    final GrTypeElement typeElement = getTypeElementGroovy();
    if (type == null) {
      if (typeElement != null) typeElement.delete();
      return;
    }

    GrTypeElement newTypeElement;
    try {
      newTypeElement = GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(type);
    }
    catch (IncorrectOperationException e) {
      GrVariableBaseImpl.LOG.error(e);
      return;
    }

    if (typeElement == null) {
      final GrModifierList modifierList = getModifierList();
      newTypeElement = (GrTypeElement)addAfter(newTypeElement, modifierList);
    }
    else {
      newTypeElement = (GrTypeElement)typeElement.replace(newTypeElement);
    }

    JavaCodeStyleManager.getInstance(getProject()).shortenClassReferences(newTypeElement);
  }

  @Override
  @Nullable
  public GrTypeElement getTypeElementGroovy() {
    final GrParameterStub stub = getStub();
    if (stub != null) {
      final String typeText = stub.getTypeText();
      if (typeText == null) {
        return null;
      }

      return GroovyPsiElementFactory.getInstance(getProject()).createTypeElement(typeText, this);
    }

    return findChildByClass(GrTypeElement.class);
  }

  @Override
  public boolean isOptional() {
    return getInitializerGroovy() != null;
  }

  @Nullable
  @Override
  public PsiElement getEllipsisDots() {
    return findChildByType(GroovyTokenTypes.mTRIPLE_DOT);
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    if (!isPhysical()) {
      final PsiFile file = getContainingFile();
      final PsiElement context = file.getContext();
      if (context != null) return new LocalSearchScope(context);
      return super.getUseScope();
    }

    final PsiElement scope = getDeclarationScope();
    if (scope instanceof GrDocCommentOwner) {
      GrDocCommentOwner owner = (GrDocCommentOwner)scope;
      final GrDocComment comment = owner.getDocComment();
      if (comment != null) {
        return new LocalSearchScope(new PsiElement[]{scope, comment});
      }
    }

    return new LocalSearchScope(scope);
  }

  @Nonnull
  @Override
  public String getName() {
    final GrParameterStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return super.getName();
  }

  @Override
  @Nonnull
  public GrModifierList getModifierList() {
    return getStubOrPsiChild(GroovyElementTypes.MODIFIERS);
  }

  @Override
  @Nonnull
  public PsiElement getDeclarationScope() {
    final GrParameterListOwner owner = PsiTreeUtil.getParentOfType(this, GrParameterListOwner.class);
    assert owner != null;
    if (owner instanceof GrForClause) return owner.getParent();
    return owner;
  }

  @Override
  public boolean isVarArgs() {
    PsiElement dots = getEllipsisDots();
    return dots != null;
  }
}
