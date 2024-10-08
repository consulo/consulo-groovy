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
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.SmartList;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryExpressionTypeCalculators;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryFacade;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * @author ilyas
 */
public abstract class GrBinaryExpressionImpl extends GrExpressionImpl implements GrBinaryExpression {
    private static final ResolveCache.PolyVariantResolver<GrBinaryExpressionImpl> RESOLVER = new ResolveCache.PolyVariantResolver<>() {
        @Nonnull
        private List<GroovyResolveResult[]> resolveSubExpressions(@Nonnull GrBinaryExpression expression, final boolean incompleteCode) {
            // to avoid SOE, resolve all binary sub-expressions starting from the innermost
            final List<GroovyResolveResult[]> subExpressions = new SmartList<>();
            expression.getLeftOperand().accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (element instanceof GrBinaryExpression) {
                        super.visitElement(element);
                    }
                }

                @Override
                protected void elementFinished(@Nonnull PsiElement element) {
                    if (element instanceof GrBinaryExpressionImpl binaryExpression) {
                        subExpressions.add(binaryExpression.multiResolve(incompleteCode));
                    }
                }
            });
            return subExpressions;
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public GroovyResolveResult[] resolve(@Nonnull GrBinaryExpressionImpl binary, boolean incompleteCode) {
            List<GroovyResolveResult[]> subExpressions = resolveSubExpressions(binary, incompleteCode);

            final IElementType opType = binary.getOperationTokenType();

            final PsiType lType = binary.getLeftType();
            if (lType == null) {
                return GroovyResolveResult.EMPTY_ARRAY;
            }

            PsiType rType = binary.getRightType();

            subExpressions.clear(); // hold resolve results until here to avoid them being gc-ed

            return TypesUtil.getOverloadedOperatorCandidates(lType, opType, binary, new PsiType[]{rType}, incompleteCode);
        }
    };

    private static final Function<GrBinaryExpressionImpl, PsiType> TYPE_CALCULATOR =
        expression -> GrBinaryExpressionTypeCalculators.getTypeCalculator(expression.getFacade()).apply(expression.getFacade());

    private final GrBinaryFacade myFacade = new GrBinaryFacade() {
        @Nonnull
        @Override
        public GrExpression getLeftOperand() {
            return GrBinaryExpressionImpl.this.getLeftOperand();
        }

        @Nullable
        @Override
        @RequiredReadAction
        public GrExpression getRightOperand() {
            return GrBinaryExpressionImpl.this.getRightOperand();
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public IElementType getOperationTokenType() {
            return GrBinaryExpressionImpl.this.getOperationTokenType();
        }

        @Nonnull
        @Override
        @RequiredReadAction
        public PsiElement getOperationToken() {
            return GrBinaryExpressionImpl.this.getOperationToken();
        }

        @Nonnull
        @Override
        public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
            return GrBinaryExpressionImpl.this.multiResolve(incompleteCode);
        }

        @Nonnull
        @Override
        public GrExpression getPsiElement() {
            return GrBinaryExpressionImpl.this;
        }
    };

    @Nullable
    @RequiredReadAction
    protected PsiType getRightType() {
        final GrExpression rightOperand = getRightOperand();
        return rightOperand == null ? null : rightOperand.getType();
    }

    @Nullable
    protected PsiType getLeftType() {
        return getLeftOperand().getType();
    }

    public GrBinaryExpressionImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    @Nonnull
    public GrExpression getLeftOperand() {
        return findNotNullChildByClass(GrExpression.class);
    }

    @Override
    @Nullable
    @RequiredReadAction
    public GrExpression getRightOperand() {
        return getLastChild() instanceof GrExpression expression ? expression : null;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public IElementType getOperationTokenType() {
        final PsiElement child = getOperationToken();
        final ASTNode node = child.getNode();
        assert node != null;
        return node.getElementType();
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public PsiElement getOperationToken() {
        return findNotNullChildByType(TokenSets.BINARY_OP_SET);
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
        visitor.visitBinaryExpression(this);
    }

    @Nonnull
    @Override
    public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
        return TypeInferenceHelper.getCurrentContext().multiResolve(this, incompleteCode, RESOLVER);
    }

    @Override
    public PsiType getType() {
        return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPE_CALCULATOR);
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
        final PsiElement token = getOperationToken();
        final int offset = token.getStartOffsetInParent();
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
        throw new IncorrectOperationException("binary expression cannot be renamed");
    }

    @Override
    @RequiredWriteAction
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("binary expression cannot be bound to anything");
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
    public PsiReference getReference() {
        return this;
    }

    private GrBinaryFacade getFacade() {
        return myFacade;
    }
}
