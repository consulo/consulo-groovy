/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.plugins.groovy.impl.codeInspection.spellchecker;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.spellcheker.text.TextContent;
import consulo.language.spellcheker.text.TextContent.Exclusion;
import consulo.language.spellcheker.text.TextContentBuilder;
import consulo.language.spellcheker.text.TextExtractor;
import consulo.language.spellcheker.text.util.EscapeUtils;
import consulo.language.spellcheker.text.util.HtmlUtils;
import consulo.language.spellcheker.text.util.PsiUtils;
import consulo.language.spellcheker.text.util.Text;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static consulo.language.spellcheker.text.TextContent.TextDomain.COMMENTS;
import static consulo.language.spellcheker.text.TextContent.TextDomain.DOCUMENTATION;
import static consulo.language.spellcheker.text.TextContent.TextDomain.LITERALS;

/**
 * Extracts natural language text from Groovy comments, GroovyDoc and string literals.
 */
@ExtensionImpl
public class GroovyTextExtractor extends TextExtractor {
    private static final TokenSet DOC_SERVICE_TOKENS = TokenSet.create(
        GroovyDocTokenTypes.mGDOC_COMMENT_START,
        GroovyDocTokenTypes.mGDOC_ASTERISKS,
        GroovyDocTokenTypes.mGDOC_COMMENT_END
    );

    private static final TokenSet DOC_TAG_VALUES = TokenSet.create(
        GroovyDocTokenTypes.mGDOC_TAG_NAME,
        GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN,
        GroovyDocElementTypes.GDOC_PARAM_REF,
        GroovyDocElementTypes.GDOC_REFERENCE_ELEMENT,
        GroovyDocElementTypes.GDOC_METHOD_REF,
        GroovyDocElementTypes.GDOC_FIELD_REF
    );

    private static final TokenSet LINE_COMMENTS = TokenSet.create(GroovyTokenTypes.mSL_COMMENT);

    private static final Set<String> TRIPLE_QUOTES = Set.of("'''", "\"\"\"");
    private static final Pattern LINE_CONTINUATION = Pattern.compile("\\\\\n");

    private static final TextContentBuilder DOC_BUILDER = TextContentBuilder.FromPsi
        .withUnknown(e -> e instanceof GrDocInlinedTag)
        .excluding(e -> DOC_SERVICE_TOKENS.contains(PsiUtilCore.getElementType(e)))
        .removingIndents(" \t")
        .removingLineSuffixes(" \t");

    private static final TextContentBuilder DOC_TAG_BUILDER = DOC_BUILDER
        .excluding(e -> DOC_TAG_VALUES.contains(PsiUtilCore.getElementType(e)) || e instanceof GrDocTagValueToken);

    private static final TextContentBuilder LINE_COMMENT_BUILDER =
        TextContentBuilder.FromPsi.removingIndents(" \t/").removingLineSuffixes(" \t");

    private static final TextContentBuilder BLOCK_COMMENT_BUILDER =
        TextContentBuilder.FromPsi.removingIndents(" \t*/").removingLineSuffixes(" \t*/");

    private static final TextContentBuilder STRING_BUILDER = TextContentBuilder.FromPsi.withUnknown(e -> e instanceof GrStringInjection);

    @Override
    @RequiredReadAction
    protected List<TextContent> buildTextContents(PsiElement root, Set<TextContent.TextDomain> allowedDomains) {
        if (root instanceof GrDocComment) {
            if (!allowedDomains.contains(DOCUMENTATION)) {
                return List.of();
            }
            return HtmlUtils.excludeHtml(DOC_BUILDER.excluding(e -> e instanceof GrDocTag && !(e instanceof GrDocInlinedTag))
                .build(root, DOCUMENTATION));
        }

        if (root instanceof GrDocTag tag && !(root instanceof GrDocInlinedTag)) {
            if (!allowedDomains.contains(DOCUMENTATION) || "author".equals(tag.getName())) {
                return List.of();
            }
            return HtmlUtils.excludeHtml(DOC_TAG_BUILDER.build(root, DOCUMENTATION));
        }

        IElementType type = PsiUtilCore.getElementType(root);
        if (type == GroovyTokenTypes.mSL_COMMENT) {
            if (!allowedDomains.contains(COMMENTS)) {
                return List.of();
            }
            List<PsiElement> roots = PsiUtils.getNotSoDistantSimilarSiblings(root, e -> LINE_COMMENTS.contains(PsiUtilCore.getElementType(e)));
            List<TextContent> fragments = ContainerUtil.mapNotNull(roots, c -> LINE_COMMENT_BUILDER.build(c, COMMENTS));
            return ContainerUtil.createMaybeSingletonList(TextContent.joinWithWhitespace('\n', fragments));
        }

        if (type == GroovyTokenTypes.mML_COMMENT) {
            if (!allowedDomains.contains(COMMENTS)) {
                return List.of();
            }
            return ContainerUtil.createMaybeSingletonList(BLOCK_COMMENT_BUILDER.build(root, COMMENTS));
        }

        if (root instanceof GrLiteral literal && allowedDomains.contains(LITERALS)) {
            return ContainerUtil.createMaybeSingletonList(buildStringContent(literal));
        }

        return List.of();
    }

    @RequiredReadAction
    private static @Nullable TextContent buildStringContent(GrLiteral literal) {
        if (!GrStringUtil.isStringLiteral(literal) || GrStringUtil.isRegex(literal)) {
            return null;
        }

        String text = literal.getText();
        String quote = GrStringUtil.getStartQuote(text);
        if (quote.isEmpty()) {
            return null;
        }
        int start = quote.length();
        int end = text.length();
        if (end >= start * 2 && text.endsWith(quote)) {
            end -= quote.length();
        }
        if (start >= end) {
            return null;
        }

        TextContent content = STRING_BUILDER.build(literal, LITERALS, new TextRange(start, end));
        if (content == null) {
            return null;
        }
        if (TRIPLE_QUOTES.contains(quote)) {
            content = content.excludeRanges(ContainerUtil.map(Text.allOccurrences(LINE_CONTINUATION, content), Exclusion::exclude));
        }
        return EscapeUtils.replaceBackslashEscapes(content).trimWhitespace();
    }

    @Override
    public Language getLanguage() {
        return GroovyLanguage.INSTANCE;
    }
}
