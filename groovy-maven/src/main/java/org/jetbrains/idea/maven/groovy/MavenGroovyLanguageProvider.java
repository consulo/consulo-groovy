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
package org.jetbrains.idea.maven.groovy;

import consulo.language.Language;
import consulo.xml.psi.xml.XmlText;
import org.jetbrains.idea.maven.dom.model.MavenDomConfiguration;
import org.jetbrains.idea.maven.plugins.api.MavenParamLanguageProvider;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class MavenGroovyLanguageProvider implements MavenParamLanguageProvider {
  @Nullable
  @Override
  public Language getLanguage(@Nonnull XmlText xmlText, @Nonnull MavenDomConfiguration configuration) {
    // Parameter 'source' of gmaven-plugin can be a peace of groovy code or file path or URL.

    String text = xmlText.getText();

    if (text.indexOf('\n') >= 0) { // URL or file path can not be multiline so it's a groovy code
      return GroovyLanguage.INSTANCE;
    }
    if (text.indexOf('(') >= 0) { // URL or file path hardly contains '(', but code usually contain '('
      return GroovyLanguage.INSTANCE;
    }

    return null;
  }
}
