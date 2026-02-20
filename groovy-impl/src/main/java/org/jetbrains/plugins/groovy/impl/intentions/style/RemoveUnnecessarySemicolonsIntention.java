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
package org.jetbrains.plugins.groovy.impl.intentions.style;

import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.HighlighterIterator;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessarySemicolonsIntention implements IntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.removeUnnecessarySemicolonsName();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        SelectionModel selectionModel = editor.getSelectionModel();
        if (!(file instanceof GroovyFileBase)) {
            return false;
        }

        if (selectionModel.hasSelection()) {
            HighlighterIterator iterator = ((EditorEx) editor).getHighlighter().createIterator(selectionModel.getSelectionStart());
            int end = selectionModel.getSelectionEnd();
            while (!iterator.atEnd()) {
                if (iterator.getTokenType() == GroovyTokenTypes.mSEMI) {
                    return true;
                }
                if (iterator.getStart() > end) {
                    return false;
                }
                iterator.advance();
            }
            return false;
        }

        int offset = editor.getCaretModel().getOffset();
        if (offset >= editor.getDocument().getTextLength()) {
            offset = editor.getDocument().getTextLength() - 1;
        }
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return false;
        }
        if (element.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
            return true;
        }

        PsiElement next = PsiTreeUtil.nextLeaf(element);
        if (next != null && next.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
            return true;
        }


        PsiElement prev = PsiTreeUtil.prevLeaf(element);
        if (prev != null && prev.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
            return true;
        }

        return false;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        SelectionModel selectionModel = editor.getSelectionModel();

        Document document = editor.getDocument();
        if (selectionModel.hasSelection()) {
            int start = selectionModel.getSelectionStart();
            int end = selectionModel.getSelectionEnd();
            final TextRange range = new TextRange(start, end);

            final ArrayList<PsiElement> semicolons = new ArrayList<PsiElement>();
            file.accept(new PsiRecursiveElementVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (!range.intersects(element.getTextRange())) {
                        return;
                    }

                    IElementType elementType = element.getNode().getElementType();
                    if (elementType == GroovyTokenTypes.mSEMI) {
                        semicolons.add(element);
                    }
                    else {
                        super.visitElement(element);
                    }
                }
            });

            boolean removed = false;
            for (PsiElement semicolon : semicolons) {
                removed = checkAndRemove(project, semicolon, document) || removed;
            }

            if (!removed) {
                CommonRefactoringUtil.showErrorHint(
                    project,
                    editor,
                    GroovyIntentionLocalize.noUnnecessarySemicolonsFound().get(),
                    GroovyIntentionLocalize.removeUnnecessarySemicolonsName().get(),
                    null
                );
            }
        }
        else {
            int offset = editor.getCaretModel().getOffset();
            if (offset >= document.getTextLength()) {
                offset = document.getTextLength() - 1;
            }
            PsiElement element = file.findElementAt(offset);
            if (element == null) {
                return;
            }
            if (checkAndRemove(project, element, document)) {
                return;
            }

            if (checkAndRemove(project, PsiTreeUtil.nextLeaf(element), document)) {
                return;
            }
            if (checkAndRemove(project, PsiTreeUtil.prevLeaf(element), document)) {
                return;
            }

            CommonRefactoringUtil.showErrorHint(
                project,
                editor,
                GroovyIntentionLocalize.noUnnecessarySemicolonsFound().get(),
                GroovyIntentionLocalize.removeUnnecessarySemicolonsName().get(),
                null
            );
        }
    }

    private static boolean checkAndRemove(Project project, @Nullable PsiElement element, Document document) {
        if (element != null && element.getNode().getElementType() == GroovyTokenTypes.mSEMI) {
            if (isSemiColonUnnecessary(element, document.getText(), project)) {
                element.delete();
                return true;
            }
        }
        return false;
    }

    private static boolean isSemiColonUnnecessary(PsiElement semicolon, String text, Project project) {
        PsiElement parent = semicolon.getParent();
        if (parent instanceof GrTraditionalForClause) {
            return false;
        }
        else if (parent instanceof GrTypeDefinitionBody) {
            return isSemiColonUnnecessaryInClassBody(semicolon, text, project);
        }
        else {
            return isSemiColonUnnecessaryInCodeBlock(semicolon, text, project);
        }
    }

    private static boolean isSemiColonUnnecessaryInCodeBlock(PsiElement semicolon, String text, Project project) {
        GrStatement prev = getPreviousStatement(semicolon, GrStatement.class);
        GrStatement next = getNextStatement(semicolon, GrStatement.class);

        if (prev == null || next == null) {
            return true;
        }

        int startOffset = prev.getTextRange().getStartOffset();
        int endOffset = next.getTextRange().getEndOffset();

        int offset = semicolon.getTextRange().getStartOffset();
        String statementWithoutSemicolon = text.substring(startOffset, offset) + text.substring(offset + 1, endOffset);
        GroovyFile file = GroovyPsiElementFactory.getInstance(project).createGroovyFile(statementWithoutSemicolon, false, null);
        GrStatement[] statements = file.getStatements();
        if (statements.length != 2) {
            return false;
        }

        return checkStatementsAreEqual(prev, statements[0]) && checkStatementsAreEqual(next, statements[1]);
    }

    private static boolean isSemiColonUnnecessaryInClassBody(PsiElement semicolon, String text, Project project) {
        GrMembersDeclaration prev = getPreviousStatement(semicolon, GrMembersDeclaration.class);
        GrMembersDeclaration next = getNextStatement(semicolon, GrMembersDeclaration.class);

        if (prev == null || next == null) {
            return true;
        }


        int startOffset = prev.getTextRange().getStartOffset();
        int endOffset = next.getTextRange().getEndOffset();

        int offset = semicolon.getTextRange().getStartOffset();
        String declarationsWithoutSemicolon = text.substring(startOffset, offset) + text.substring(offset + 1, endOffset);

        PsiElement parent = semicolon.getParent().getParent();

        String prefix =
            parent instanceof GrClassDefinition ? "class" : parent instanceof GrEnumTypeDefinition ? "enum" : parent instanceof GrInterfaceDefinition ? "interface" : parent instanceof
                GrAnnotationTypeDefinition ? "@interface" : parent instanceof GrAnonymousClassDefinition ? "class" : "class";
        GroovyFile file = GroovyPsiElementFactory.getInstance(project)
            .createGroovyFile(
                prefix + " Name {\n" + declarationsWithoutSemicolon + "\n}",
                false,
                null
            );
        GrTypeDefinition[] typeDefs = file.getTypeDefinitions();
        if (typeDefs.length != 1) {
            return false;
        }

        GrTypeDefinition clazz = typeDefs[0];
        GrMembersDeclaration[] declarations = clazz.getMemberDeclarations();
        if (declarations.length != 2) {
            return false;
        }

        return checkStatementsAreEqual(prev, declarations[0]) && checkStatementsAreEqual(next, declarations[1]);
    }


    private static <T extends PsiElement> boolean checkStatementsAreEqual(T before, T after) {
        if (before instanceof GrConstructorInvocation) {
            return after instanceof GrMethodCall && before.getText().equals(after.getText());
        }
        else {
            return PsiUtil.checkPsiElementsAreEqual(before, after);
        }
    }

    @Nullable
    private static <T extends PsiElement> T getPreviousStatement(PsiElement semicolon, Class<T> instanceOf) {
        PsiElement prev = PsiUtil.skipWhitespacesAndComments(semicolon.getPrevSibling(), false);
        if (instanceOf.isInstance(prev)) {
            return (T) prev;
        }
        return null;
    }

    @Nullable
    private static <T extends PsiElement> T getNextStatement(PsiElement semicolon, Class<T> instaceOf) {
        PsiElement next = PsiUtil.skipWhitespacesAndComments(semicolon.getNextSibling(), true);
        if (instaceOf.isInstance(next)) {
            return (T) next;
        }
        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
