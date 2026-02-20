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
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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

    @Override
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
    @RequiredReadAction
    public IElementType getOperationTokenType() {
        return getOperationToken().getNode().getElementType();
    }

    @Override
    @Nonnull
    @RequiredReadAction
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
    @RequiredReadAction
    public PsiElement getElement() {
        return this;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public TextRange getRangeInElement() {
        PsiElement token = getOperationToken();
        int offset = token.getStartOffsetInParent();
        return new TextRange(offset, offset + token.getTextLength());
    }

    @Override
    @RequiredReadAction
    public PsiElement resolve() {
        return PsiImplUtil.extractUniqueElement(multiResolve(false));
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getCanonicalText() {
        return getText();
    }

    @Override
    @RequiredWriteAction
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new IncorrectOperationException("assignment expression cannot be renamed");
    }

    @Override
    @RequiredWriteAction
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("assignment expression cannot be bound to anything");
    }

    @Override
    @RequiredReadAction
    public boolean isReferenceTo(PsiElement element) {
        return getManager().areElementsEquivalent(resolve(), element);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @RequiredReadAction
    public boolean isSoft() {
        return false;
    }

    @Override
    @RequiredReadAction
    public PsiReference getReference() {
        IElementType operationToken = getOperationTokenType();
        if (operationToken == GroovyTokenTypes.mASSIGN) {
            return null;
        }

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
        @RequiredReadAction
        public IElementType getOperationTokenType() {
            return GrAssignmentExpressionImpl.this.getOperationTokenType();
        }

        @Nonnull
        @Override
        @RequiredReadAction
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


    private static final ResolveCache.PolyVariantResolver<GrAssignmentExpressionImpl> RESOLVER = new ResolveCache.PolyVariantResolver<>() {
        @Nonnull
        @Override
        @RequiredReadAction
        public GroovyResolveResult[] resolve(@Nonnull GrAssignmentExpressionImpl assignmentExpression, boolean incompleteCode) {
            IElementType opType = assignmentExpression.getOperationTokenType();
            if (opType == GroovyTokenTypes.mASSIGN) {
                return GroovyResolveResult.EMPTY_ARRAY;
            }

            GrExpression lValue = assignmentExpression.getLValue();
            PsiType lType;
            if (lValue instanceof GrIndexProperty indexProperty) {
                /*
                now we have something like map[i] += 2. It equals to map.putAt(i, map.getAt(i).plus(2))
                by default map[i] resolves to putAt, but we need getAt(). so this hack is for it =)
                 */
                lType = indexProperty.getGetterType();
            }
            else {
                lType = lValue.getType();
            }
            if (lType == null) {
                return GroovyResolveResult.EMPTY_ARRAY;
            }

            PsiType rType = GrBinaryExpressionUtil.getRightType(assignmentExpression.getFacade());

            IElementType operatorToken = TokenSets.ASSIGNMENTS_TO_OPERATORS.get(opType);
            return TypesUtil.getOverloadedOperatorCandidates(lType, operatorToken, lValue, new PsiType[]{rType});
        }
    };

    private static final Function<GrAssignmentExpressionImpl, PsiType> TYPE_CALCULATOR = expression -> {
        Function<GrBinaryFacade, PsiType> calculator = GrBinaryExpressionTypeCalculators.getTypeCalculator(expression.getFacade());
        return calculator.apply(expression.getFacade());
    };
}
