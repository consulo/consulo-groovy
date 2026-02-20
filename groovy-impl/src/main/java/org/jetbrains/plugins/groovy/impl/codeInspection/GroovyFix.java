/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.ReadonlyStatusHandler;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import jakarta.annotation.Nonnull;

public abstract class GroovyFix implements LocalQuickFix {
    public static final GroovyFix[] EMPTY_ARRAY = new GroovyFix[0];

    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PsiElement problemElement = descriptor.getPsiElement();
        if (problemElement == null || !problemElement.isValid()) {
            return;
        }
        if (isQuickFixOnReadOnlyFile(problemElement)) {
            return;
        }
        try {
            doFix(project, descriptor);
        }
        catch (IncorrectOperationException e) {
            Class<? extends GroovyFix> aClass = getClass();
            String className = aClass.getName();
            Logger logger = Logger.getInstance(className);
            logger.error(e);
        }
    }

    protected abstract void doFix(Project project, ProblemDescriptor descriptor)
        throws IncorrectOperationException;

    private static boolean isQuickFixOnReadOnlyFile(PsiElement problemElement) {
        PsiFile containingPsiFile = problemElement.getContainingFile();
        if (containingPsiFile == null) {
            return false;
        }
        VirtualFile virtualFile = containingPsiFile.getVirtualFile();
        JavaPsiFacade facade = JavaPsiFacade.getInstance(problemElement.getProject());
        Project project = facade.getProject();
        ReadonlyStatusHandler handler = ReadonlyStatusHandler.getInstance(project);
        ReadonlyStatusHandler.OperationStatus status = handler.ensureFilesWritable(virtualFile);
        return status.hasReadonlyFiles();
    }

    protected static void replaceExpression(GrExpression expression, String newExpression) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expression.getProject());
        GrExpression newCall = factory.createExpressionFromText(newExpression);
        expression.replaceWithExpression(newCall, true);
    }

    protected static void replaceStatement(GrStatement statement, String newStatement) {
        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
        GrStatement newCall = (GrStatement) factory.createTopElementFromText(newStatement);
        statement.replaceWithStatement(newCall);
    }

    /**
     * unwraps surrounding blocks from newStatement.
     */
    protected static void replaceStatement(GrStatement oldStatement, GrStatement newStatement) throws IncorrectOperationException {
        if (newStatement instanceof GrBlockStatement) {
            GrBlockStatement blockStatement = (GrBlockStatement) newStatement;
            GrOpenBlock openBlock = blockStatement.getBlock();
            GrStatement[] statements = openBlock.getStatements();
            if (statements.length == 0) {
                oldStatement.removeStatement();
            }
            else {
                PsiElement parent = oldStatement.getParent();
                if (parent instanceof GrStatementOwner) {
                    GrStatementOwner statementOwner = (GrStatementOwner) parent;
                    for (GrStatement statement : statements) {
                        statementOwner.addStatementBefore(statement, oldStatement);
                    }
                    oldStatement.removeStatement();
                }
                else if (parent instanceof GrControlStatement) {
                    oldStatement.replace(newStatement);
                }
            }
        }
        else {
            oldStatement.replaceWithStatement(newStatement);
        }
    }
}