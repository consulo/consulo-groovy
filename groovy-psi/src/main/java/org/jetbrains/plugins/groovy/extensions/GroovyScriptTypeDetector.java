/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.ui.image.Image;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import jakarta.annotation.Nonnull;

/**
 * @author sergey.evdokimov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class GroovyScriptTypeDetector {
  public static final ExtensionPointName<GroovyScriptTypeDetector> EP_NAME =
    ExtensionPointName.create(GroovyScriptTypeDetector.class);

  private final GroovyScriptType myScriptType;

  protected GroovyScriptTypeDetector(GroovyScriptType scriptType) {
    myScriptType = scriptType;
  }

  @Nonnull
  public final GroovyScriptType getScriptType() {
    return myScriptType;
  }

  public abstract boolean isSpecificScriptFile(@Nonnull GroovyFile script);

  @Nonnull
  public static Image getIcon(@Nonnull GroovyFile script) {
    for (GroovyScriptTypeDetector detector : EP_NAME.getExtensionList()) {
      if (detector.isSpecificScriptFile(script)) {
        return detector.getScriptType().getScriptIcon();
      }
    }

    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  public static GlobalSearchScope patchResolveScope(@Nonnull GroovyFile script, GlobalSearchScope scope) {
    for (GroovyScriptTypeDetector detector : EP_NAME.getExtensionList()) {
      if (detector.isSpecificScriptFile(script)) {
        return detector.getScriptType().patchResolveScope(script, scope);
      }
    }
    return scope;
  }
}
