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
package org.jetbrains.plugins.groovy.impl.intentions.control;

import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

public class MergeElseIfIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.mergeElseIfIntentionName();
    }

    @Nonnull
    public PsiElementPredicate getElementPredicate() {
        return new MergeElseIfPredicate();
    }

    public void processIntention(
        @Nonnull PsiElement element,
        Project project,
        Editor editor
    ) throws IncorrectOperationException {
        GrIfStatement parentStatement = (GrIfStatement) element;
        GrBlockStatement elseBlockStatement = (GrBlockStatement) parentStatement.getElseBranch();
        assert elseBlockStatement != null;
        GrOpenBlock elseBranch = elseBlockStatement.getBlock();
        GrStatement elseBranchContents = elseBranch.getStatements()[0];
        PsiImplUtil.replaceStatement(
            "if(" + parentStatement.getCondition().getText() + ")" +
                parentStatement.getThenBranch().getText() +
                "else " + elseBranchContents.getText(),
            parentStatement
        );
    }
}
