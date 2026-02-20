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
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;

/**
 * @author ilyas
 */
public class GrApplicationStatementImpl extends GrMethodCallImpl implements GrApplicationStatement {
    public GrApplicationStatementImpl(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
        visitor.visitApplicationStatement(this);
    }

    @Override
    public String toString() {
        return "Call expression";
    }

    @Override
    @RequiredReadAction
    public GrCommandArgumentList getArgumentList() {
        return findChildByClass(GrCommandArgumentList.class);
    }

    @Override
    public GrNamedArgument addNamedArgument(GrNamedArgument namedArgument) throws IncorrectOperationException {
        GrCommandArgumentList list = getArgumentList();
        assert list != null;
        return list.addNamedArgument(namedArgument);
    }
}
