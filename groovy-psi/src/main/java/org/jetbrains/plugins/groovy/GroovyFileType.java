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

package org.jetbrains.plugins.groovy;

import javax.annotation.Nonnull;
import javax.swing.Icon;

import org.jetbrains.annotations.NonNls;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import icons.JetgroovyIcons;

/**
 * Represents Groovy file properites, such as extension etc.
 *
 * @author ilyas
 */
public class GroovyFileType extends LanguageFileType {

  public static final GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  @Deprecated
  public static final Language GROOVY_LANGUAGE = GroovyLanguage.INSTANCE;
  @NonNls public static final String DEFAULT_EXTENSION = "groovy";

  private GroovyFileType() {
    super(GroovyLanguage.INSTANCE);
  }

  @Nonnull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @NonNls
  @Nonnull
  public String getDescription() {
    return "Groovy Files";
  }

  @Nonnull
  @NonNls
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }
}
