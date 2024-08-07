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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiParameter;
import com.intellij.java.language.psi.PsiType;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import jakarta.annotation.Nullable;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrUnaryExpressionImpl extends GrExpressionImpl implements GrUnaryExpression {

  private static final Function<GrUnaryExpressionImpl,PsiType> TYPE_CALCULATOR = new Function<GrUnaryExpressionImpl, PsiType>() {
    @Nullable
    @Override
    public PsiType apply(GrUnaryExpressionImpl unary) {
      final GroovyResolveResult resolveResult = PsiImplUtil.extractUniqueResult(unary.multiResolve(false));

      if (isIncDecNumber(resolveResult)) {
        return unary.getOperand().getType();
      }

      final PsiType substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult, unary, PsiType.EMPTY_ARRAY);
      if (substituted != null) {
        return substituted;
      }

      GrExpression operand = unary.getOperand();
      if (operand == null) return null;

      final PsiType type = operand.getType();
      if (TypesUtil.isNumericType(type)) {
        return type;
      }

      return null;
    }

    //hack for DGM.next(Number):Number
    private boolean isIncDecNumber(GroovyResolveResult result) {
      PsiElement element = result.getElement();

      if (!(element instanceof PsiMethod)) return false;

      final PsiMethod method = element instanceof GrGdkMethod ? ((GrGdkMethod)element).getStaticMethod() : (PsiMethod)element;

      final String name = method.getName();
      if (!"next".equals(name) && !"previous".equals(name)) return false;

      if (!PsiUtil.isDGMMethod(method)) return false;

      final PsiParameter[] parameters = method.getParameterList().getParameters();
      if (parameters.length != 1) return false;

      if (!parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_NUMBER)) return false;

      return true;
    }
  };

  private static final ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl> OUR_RESOLVER = new ResolveCache.PolyVariantResolver<GrUnaryExpressionImpl>() {
    @Nonnull
    @Override
    public GroovyResolveResult[] resolve(@Nonnull GrUnaryExpressionImpl unary, boolean incompleteCode) {
      final GrExpression operand = unary.getOperand();
      if (operand == null) return GroovyResolveResult.EMPTY_ARRAY;

      final PsiType type = operand.getType();
      if (type == null) return GroovyResolveResult.EMPTY_ARRAY;

      return TypesUtil.getOverloadedUnaryOperatorCandidates(type, unary.getOperationTokenType(), operand, PsiType.EMPTY_ARRAY);
    }
  };

  public GrUnaryExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Unary expression";
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  @Nonnull
  public IElementType getOperationTokenType() {
    PsiElement opElement = getOperationToken();
    ASTNode node = opElement.getNode();
    assert node != null;
    return node.getElementType();
  }

  @Override
  @Nonnull
  public PsiElement getOperationToken() {
    PsiElement opElement = findChildByType(TokenSets.UNARY_OP_SET);
    assert opElement != null;
    return opElement;
  }

  @Override
  public GrExpression getOperand() {
    return findExpressionChild(this);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitUnaryExpression(this);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, OUR_RESOLVER);
  }

  @Override
  public boolean isPostfix() {
    return getFirstChild() instanceof GrExpression;
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement opToken = getOperationToken();
    final int offset = opToken.getStartOffsetInParent();
    return new TextRange(offset, offset + opToken.getTextLength());
  }

  @Override
  public PsiElement resolve() {
    return PsiImplUtil.extractUniqueElement(multiResolve(false));
  }

  @Nonnull
  @Override
  public String getCanonicalText() {
    return getText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException("unary expression cannot be renamed to anything");
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("unary expression cannot be bounded to anything");
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getManager().areElementsEquivalent(resolve(), element);
  }

  @Nonnull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }
}
