/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.closure;

import javax.annotation.Nonnull;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class MakeClosureCallExplicitIntention extends Intention {


    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new ImplicitClosureCallPredicate();
    }

    public void processIntention(@Nonnull PsiElement element, Project project, Editor editor)
            throws IncorrectOperationException
	{
        final GrMethodCallExpression expression =
                (GrMethodCallExpression) element;
        final GrExpression invokedExpression = expression.getInvokedExpression();
        final GrArgumentList argList = expression.getArgumentList();
        final GrClosableBlock[] closureArgs = expression.getClosureArguments();
        final StringBuilder newExpression = new StringBuilder();
        newExpression.append(invokedExpression.getText());
        newExpression.append(".call");
        if (argList != null) {
            newExpression.append(argList.getText());
        }
        for (GrClosableBlock closureArg : closureArgs) {
            newExpression.append(closureArg.getText());
        }
		PsiImplUtil.replaceExpression(newExpression.toString(), expression);
    }
}
