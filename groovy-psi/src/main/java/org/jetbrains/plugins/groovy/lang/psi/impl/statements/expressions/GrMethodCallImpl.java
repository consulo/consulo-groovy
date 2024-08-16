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

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.navigation.ItemPresentationProvider;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrCallExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrCallExpressionTypeCalculator;

import jakarta.annotation.Nonnull;

import java.util.function.Function;

/**
 * @author Maxim.Medvedev
 */
public abstract class GrMethodCallImpl extends GrCallExpressionImpl implements GrMethodCall {
    private static final Function<GrMethodCall, PsiType> METHOD_CALL_TYPES_CALCULATOR = new Function<>() {
        @Override
        @Nullable
        public PsiType apply(GrMethodCall callExpression) {
            GrExpression invokedExpression = callExpression.getInvokedExpression();

            GroovyResolveResult[] resolveResults = invokedExpression instanceof GrReferenceExpression referenceExpression
                ? referenceExpression.multiResolve(false)
                : GroovyResolveResult.EMPTY_ARRAY;

            for (GrCallExpressionTypeCalculator typeCalculator : GrCallExpressionTypeCalculator.EP_NAME.getExtensionList()) {
                PsiType res = typeCalculator.calculateReturnType(callExpression, resolveResults);
                if (res != null) {
                    return res;
                }
            }

            return null;
        }
    };

    public GrMethodCallImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    @Nonnull
    public GroovyResolveResult[] getCallVariants(@Nullable GrExpression upToArgument) {
        final GrExpression invoked = getInvokedExpression();
        if (!(invoked instanceof GrReferenceExpressionImpl)) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }

        return ((GrReferenceExpressionImpl)invoked).getCallVariants(upToArgument);
    }

    @Override
    @Nonnull
    public GrExpression getInvokedExpression() {
        for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
            if (cur instanceof GrExpression) {
                return (GrExpression)cur;
            }
        }
        throw new IncorrectOperationException("invoked expression must not be null");
    }

    @Override
    public PsiMethod resolveMethod() {
        final GrExpression methodExpr = getInvokedExpression();
        if (methodExpr instanceof GrReferenceExpression referenceExpression) {
            return referenceExpression.resolve() instanceof PsiMethod method ? method : null;
        }

        return null;
    }

    @Nonnull
    @Override
    public GroovyResolveResult advancedResolve() {
        final GrExpression methodExpr = getInvokedExpression();
        return methodExpr instanceof GrReferenceExpression referenceExpression
            ? referenceExpression.advancedResolve()
            : GroovyResolveResult.EMPTY_RESULT;
    }

    @Override
    public PsiType getType() {
        return TypeInferenceHelper.getCurrentContext().getExpressionType(this, METHOD_CALL_TYPES_CALCULATOR);
    }

    @Override
    public boolean isCommandExpression() {
        final GrExpression expression = getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression) || ((GrReferenceExpression)expression).getQualifier() == null) {
            return false;
        }

        return ((GrReferenceExpression)expression).getDotToken() == null;
    }

    @Nonnull
    @Override
    public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
        GrExpression expression = getInvokedExpression();
        if (!(expression instanceof GrReferenceExpression)) {
            return GroovyResolveResult.EMPTY_ARRAY;
        }
        return ((GrReferenceExpression)expression).multiResolve(incompleteCode);
    }

    @Override
    public ItemPresentation getPresentation() {
        return ItemPresentationProvider.getItemPresentation(this);
    }
}
