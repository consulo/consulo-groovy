/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import consulo.language.ast.IElementType;
import consulo.language.lexer.FlexLexer;
import consulo.language.ast.TokenType;
import consulo.util.lang.CharArrayUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.*;

%%

%class _GroovyDocLexer
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%state TOP_LEVEL
%state ASTERISKS
%state AFTER_ASTERISKS
%state COMMENT_DATA
%xstate AFTER_BRACE
%state AFTER_PLAIN_TAG_NAME
%state AFTER_TAG_NAME
%state TAG_VALUE
%state TAG_VALUE_IN_ANGLES
%state TAG_VALUE_IN_PAREN

WS_CHARS = [\ \t\f]
NL_CHARS = [\n\r]
WS_NL_CHARS = {WS_CHARS} | {NL_CHARS}
WS_NL = {WS_NL_CHARS}+
WS = {WS_CHARS}+
DIGIT = [0-9]
ALPHA = [:jletter:]
IDENTIFIER = {ALPHA} ({ALPHA} | {DIGIT} | [":.-"])*
TAG_WITH_VALUE = "param" | "link" | "linkplain" | "see" | "value" | "throws" | "attr"
VALUE_IDENTIFIER = ({ALPHA} | {DIGIT} | [_\."$"\[\]])+
%%

<YYINITIAL> "/**"               { yybegin(AFTER_ASTERISKS); return mGDOC_COMMENT_START; }

<TOP_LEVEL> {
  {WS_NL}                       { return TokenType.WHITE_SPACE; }
  "*"                           { yybegin(ASTERISKS); return mGDOC_ASTERISKS; }
}

<ASTERISKS> {
  "*"                           { return mGDOC_ASTERISKS; }
  [^]                           { yypushback(1); yybegin(AFTER_ASTERISKS); }
}

<AFTER_ASTERISKS, COMMENT_DATA> {
  {WS}                          { return mGDOC_COMMENT_DATA; }
  {NL_CHARS}+{WS_NL_CHARS}*     { yybegin(TOP_LEVEL); return TokenType.WHITE_SPACE; }
}

<TOP_LEVEL, AFTER_ASTERISKS, COMMENT_DATA> {
  "{"                           { yybegin(AFTER_BRACE); return mGDOC_INLINE_TAG_START; }
  "}"                           { yybegin(COMMENT_DATA); return mGDOC_INLINE_TAG_END; }
  .                             { yybegin(COMMENT_DATA); return mGDOC_COMMENT_DATA; }
}

<TOP_LEVEL, AFTER_ASTERISKS, AFTER_BRACE> {
  "@"{TAG_WITH_VALUE}           { yybegin(AFTER_TAG_NAME); return mGDOC_TAG_NAME; }
  "@"{IDENTIFIER}               { yybegin(AFTER_PLAIN_TAG_NAME); return mGDOC_TAG_NAME; }
}

<AFTER_BRACE> [^]               { yypushback(1); yybegin(COMMENT_DATA); }

<AFTER_TAG_NAME> {
  {WS_NL}                       { yybegin(TAG_VALUE); return TokenType.WHITE_SPACE;}
  [^]                           { yypushback(1); yybegin(COMMENT_DATA); }
}

<AFTER_PLAIN_TAG_NAME> {
  {WS}                          { yybegin(COMMENT_DATA); return TokenType.WHITE_SPACE; }
  {NL_CHARS}                    { yybegin(TOP_LEVEL); return TokenType.WHITE_SPACE; }
  "}"                           { yybegin(COMMENT_DATA); return mGDOC_INLINE_TAG_END; }
}

<TAG_VALUE> {
  {WS}                          { yybegin(COMMENT_DATA); return TokenType.WHITE_SPACE; }
  {VALUE_IDENTIFIER}            { return mGDOC_TAG_VALUE_TOKEN; }
  ","                           { return mGDOC_TAG_VALUE_COMMA; }
  "<"{IDENTIFIER}">"            { yybegin(COMMENT_DATA); return mGDOC_TAG_VALUE_TOKEN; }
  "("                           { yybegin(TAG_VALUE_IN_PAREN); return mGDOC_TAG_VALUE_LPAREN; }
  "#"                           { return mGDOC_TAG_VALUE_SHARP_TOKEN; }
  [^]                           { yypushback(1); yybegin(COMMENT_DATA); }
}

<TAG_VALUE_IN_PAREN> {
  {WS_NL}                       { return TokenType.WHITE_SPACE; }
  {VALUE_IDENTIFIER}            { return mGDOC_TAG_VALUE_TOKEN; }
  ","                           { return mGDOC_TAG_VALUE_COMMA; }
  ")"                           { yybegin(TAG_VALUE); return mGDOC_TAG_VALUE_RPAREN; }
}

"*/"                            { return mGDOC_COMMENT_END; }
[^]                             { return mGDOC_COMMENT_DATA; }