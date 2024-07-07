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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import consulo.project.Project;
import com.intellij.java.language.psi.JavaPsiFacade;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GrChangeSignatureUtil {
  @Nonnull
  public static String getNameWithQuotesIfNeeded(@Nonnull final String originalName, @Nonnull final Project project) {
    return JavaPsiFacade.getInstance(project).getNameHelper().isIdentifier(originalName)
           ? originalName
           : GrStringUtil.getLiteralTextByValue(originalName).toString();
  }
}
