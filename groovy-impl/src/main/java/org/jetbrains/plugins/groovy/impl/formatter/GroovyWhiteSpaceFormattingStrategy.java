/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.formatter;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.WhiteSpaceFormattingStrategyAdapter;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyWhiteSpaceFormattingStrategy extends WhiteSpaceFormattingStrategyAdapter {
  @Override
  public boolean containsWhitespacesOnly(@Nonnull ASTNode node) {
    return node.getElementType() == GroovyTokenTypes.mNLS;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
