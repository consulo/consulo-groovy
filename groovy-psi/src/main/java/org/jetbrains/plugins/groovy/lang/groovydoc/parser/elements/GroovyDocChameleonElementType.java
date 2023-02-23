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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser.elements;

import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import consulo.language.Language;
import consulo.language.ast.ILazyParseableElementType;

/**
 * @author ilyas
 */
public abstract class GroovyDocChameleonElementType extends ILazyParseableElementType {
  public GroovyDocChameleonElementType(@NonNls String debugName) {
    super(debugName);
  }

  @Nonnull
  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
  }

}
