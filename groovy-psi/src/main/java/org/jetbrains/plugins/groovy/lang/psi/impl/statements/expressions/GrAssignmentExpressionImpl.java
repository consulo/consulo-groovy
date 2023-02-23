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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.java.language.psi.PsiType;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryExpressionTypeCalculators;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryExpressionUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryFacade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * @author ilyas
 */
public class GrAssignmentExpressionImpl extends GrExpressionImpl implements GrAssignmentExpression {

  private GrBinaryFacade getFacade() {
    return myFacade;
  }

  public GrAssignmentExpressionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Assignment expression";
  }

  @Override
  @Nonnull
  public GrExpression getLValue() {
    return findExpressionChild(this);
  }

  @Override
  @Nullable
  public GrExpression getRValue() {
    GrExpression[] exprs = findChildrenByClass(GrExpression.class);
    if (exprs.length > 1) {
      return exprs[1];
    }
    return null;
  }

  @Override
  @Nonnull
  public IElementType getOperationTokenType() {
    return getOperationToken().getNode().getElementType();
  }

  @Override
  @Nonnull
  public PsiElement getOperationToken() {
    return findNotNullChildByType(TokenSets.ASSIGN_OP_SET);
  }

  @Override
  public PsiType getType() {
    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAssignmentExpression(this);
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public TextRange getRangeInElement() {
    final PsiElement token = getOperationToken();
    final int offset = token.getStartOffsetInParent();
    return new TextRange(offset, offset + token.getTextLength());
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
    throw new IncorrectOperationException("assignment expression cannot be renamed");
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("assignment expression cannot be bound to anything");
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
    final IElementType operationToken = getOperationTokenType();
    if (operationToken == GroovyTokenTypes.mASSIGN) return null;

    return this;
  }

  private final GrBinaryFacade myFacade = new GrBinaryFacade() {
    @Nonnull
    @Override
    public GrExpression getLeftOperand() {
      return getLValue();
    }

    @Nullable
    @Override
    public GrExpression getRightOperand() {
      return getRValue();
    }

    @Nonnull
    @Override
    public IElementType getOperationTokenType() {
      return GrAssignmentExpressionImpl.this.getOperationTokenType();
    }

    @Nonnull
    @Override
    public PsiElement getOperationToken() {
      return GrAssignmentExpressionImpl.this.getOperationToken();
    }

    @Nonnull
    @Override
    public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
      return GrAssignmentExpressionImpl.this.multiResolve(false);
    }

    @Nonnull
    @Override
    public GrExpression getPsiElement() {
      return GrAssignmentExpressionImpl.this;
    }
  };


  private static final ResolveCache.PolyVariantResolver<GrAssignmentExpressionImpl> RESOLVER =
    new ResolveCache.PolyVariantResolver<GrAssignmentExpressionImpl>() {
      @Nonnull
      @Override
      public GroovyResolveResult[] resolve(@Nonnull GrAssignmentExpressionImpl assignmentExpression, boolean incompleteCode) {
        final IElementType opType = assignmentExpression.getOperationTokenType();
        if (opType == GroovyTokenTypes.mASSIGN) return GroovyResolveResult.EMPTY_ARRAY;

        final GrExpression lValue = assignmentExpression.getLValue();
        final PsiType lType;
        if (lValue instanceof GrIndexProperty) {
          /*
          now we have something like map[i] += 2. It equals to map.putAt(i, map.getAt(i).plus(2))
          by default map[i] resolves to putAt, but we need getAt(). so this hack is for it =)
           */
          lType = ((GrIndexProperty)lValue).getGetterType();
        }
        else {
          lType = lValue.getType();
        }
        if (lType == null) return GroovyResolveResult.EMPTY_ARRAY;

        PsiType rType = GrBinaryExpressionUtil.getRightType(assignmentExpression.getFacade());

        final IElementType operatorToken = TokenSets.ASSIGNMENTS_TO_OPERATORS.get(opType);
        return TypesUtil.getOverloadedOperatorCandidates(lType, operatorToken, lValue, new PsiType[]{rType});
      }
    };

  private static final Function<GrAssignmentExpressionImpl, PsiType> TYPE_CALCULATOR = new Function<GrAssignmentExpressionImpl, PsiType>() {
    @Override
    public PsiType apply(GrAssignmentExpressionImpl expression) {
      final Function<GrBinaryFacade, PsiType> calculator = GrBinaryExpressionTypeCalculators.getTypeCalculator(expression.getFacade());
      return calculator.apply(expression.getFacade());
    }
  };
}
