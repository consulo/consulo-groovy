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
import consulo.language.ast.ASTNode;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrElvisExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public class GrElvisExprImpl extends GrConditionalExprImpl implements GrElvisExpression {
    public GrElvisExprImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    public String toString() {
        return "Elvis expression";
    }

    @Override
    @RequiredReadAction
    public GrExpression getThenBranch() {
        return getCondition();
    }

    @Override
    @RequiredReadAction
    public GrExpression getElseBranch() {
        GrExpression[] exprs = findChildrenByClass(GrExpression.class);
        return exprs.length > 1 ? exprs[1] : null;
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
        visitor.visitElvisExpression(this);
    }
}
