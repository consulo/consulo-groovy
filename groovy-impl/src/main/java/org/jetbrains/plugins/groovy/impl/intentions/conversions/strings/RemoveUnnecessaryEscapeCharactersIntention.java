/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.conversions.strings;

import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;

import static org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil.*;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessaryEscapeCharactersIntention extends Intention {
    public static final String HINT = "Remove unnecessary escape characters";

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.removeUnnecessaryEscapeCharactersIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        final Document document = editor.getDocument();
        final TextRange range = element.getTextRange();

        document.replaceString(range.getStartOffset(), range.getEndOffset(), removeUnnecessaryEscapeSymbols((GrLiteral) element));
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            public boolean satisfiedBy(PsiElement element) {
                if (!(element instanceof GrLiteral)) {
                    return false;
                }

                String text = element.getText();
                return getStartQuote(text) != null && !removeUnnecessaryEscapeSymbols((GrLiteral) element).equals(text);
            }
        };
    }

    private static String removeUnnecessaryEscapeSymbols(final GrLiteral literal) {
        final String text = literal.getText();
        final String quote = getStartQuote(text);
        final String value = removeQuotes(text);

        final StringBuilder buffer = new StringBuilder();
        buffer.append(quote);

        if (quote == "'") {
            escapeAndUnescapeSymbols(value, "", "\"$", buffer);
        }
        else if (quote == "'''") {
            int position = buffer.length();
            escapeAndUnescapeSymbols(value, "", "\"'$n", buffer);
            fixAllTripleQuotes(buffer, position);
        }
        else if (quote == "\"") {
            if (literal instanceof GrString) {
                final ASTNode node = literal.getNode();
                for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                    final IElementType type = child.getElementType();
                    if (type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END) {
                        continue;
                    }
                    if (type == GroovyElementTypes.GSTRING_INJECTION) {
                        buffer.append(child.getText());
                    }
                    else {
                        escapeAndUnescapeSymbols(child.getText(), "", "'", buffer);
                    }
                }
            }
            else {
                escapeAndUnescapeSymbols(value, "", "'", buffer);
            }
        }
        else if (quote == "\"\"\"") {
            if (literal instanceof GrString) {
                final ASTNode node = literal.getNode();
                for (ASTNode child = node.getFirstChildNode(); child != null; child = child.getTreeNext()) {
                    final IElementType type = child.getElementType();
                    if (type == GroovyTokenTypes.mGSTRING_BEGIN || type == GroovyTokenTypes.mGSTRING_END) {
                        continue;
                    }
                    if (type == GroovyElementTypes.GSTRING_INJECTION) {
                        buffer.append(child.getText());
                    }
                    else {
                        final int position = buffer.length();
                        escapeAndUnescapeSymbols(child.getText(), "", "\"'n", buffer);
                        fixAllTripleDoubleQuotes(buffer, position);
                    }
                }
            }
            else {
                final int position = buffer.length();
                escapeAndUnescapeSymbols(value, "", "\"'n", buffer);
                fixAllTripleDoubleQuotes(buffer, position);
            }
        }
        else {
            return text;
        }

        buffer.append(quote);

        return buffer.toString();
    }
}
