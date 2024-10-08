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

import consulo.annotation.access.RequiredReadAction;
import jakarta.annotation.Nonnull;

import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import consulo.language.ast.ASTNode;

/**
 * @author ven
 */
public abstract class GrCallImpl extends GroovyPsiElementImpl implements GrCall {
    public GrCallImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    @RequiredReadAction
    public GrArgumentList getArgumentList() {
        for (PsiElement cur = this.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
            if (cur instanceof GrArgumentList argumentList) {
                return argumentList;
            }
        }
        return null;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public GrNamedArgument[] getNamedArguments() {
        GrArgumentList argList = getArgumentList();
        return argList != null ? argList.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
    }

    @Override
    @Nonnull
    @RequiredReadAction
    public GrExpression[] getExpressionArguments() {
        GrArgumentList argList = getArgumentList();
        return argList != null ? argList.getExpressionArguments() : GrExpression.EMPTY_ARRAY;
    }

    @Override
    public GrNamedArgument addNamedArgument(final GrNamedArgument namedArgument) throws IncorrectOperationException {
        GrArgumentList list = getArgumentList();
        assert list != null;
        if (list.getText().trim().isEmpty()) {
            final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
            final GrArgumentList newList = factory.createExpressionArgumentList();
            list = (GrArgumentList)list.replace(newList);
        }
        return list.addNamedArgument(namedArgument);
    }

    @Nonnull
    @Override
    public GrClosableBlock[] getClosureArguments() {
        return GrClosableBlock.EMPTY_ARRAY;
    }
}
