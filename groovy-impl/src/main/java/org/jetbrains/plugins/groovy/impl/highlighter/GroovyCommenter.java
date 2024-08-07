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

package org.jetbrains.plugins.groovy.impl.highlighter;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.CodeDocumentationAwareCommenter;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiComment;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyCommenter implements CodeDocumentationAwareCommenter {
  @Override
  public String getLineCommentPrefix() {
    return "//";
  }

  @Override
  public String getBlockCommentPrefix() {
    return "/*";
  }

  @Override
  public String getBlockCommentSuffix() {
    return "*/";
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  @Nullable
  public IElementType getLineCommentTokenType() {
    return GroovyTokenTypes.mSL_COMMENT;
  }

  @Override
  @Nullable
  public IElementType getBlockCommentTokenType() {
    return GroovyTokenTypes.mML_COMMENT;
  }

  @Override
  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return GroovyDocElementTypes.GROOVY_DOC_COMMENT;
  }

  @Override
  @Nullable
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Override
  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Override
  @Nullable
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return element.getText().startsWith(getDocumentationCommentPrefix());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
