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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.UnfairLocalInspectionTool;

import javax.annotation.Nonnull;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyUnusedDeclarationInspection extends GroovySuppressableInspectionTool implements UnfairLocalInspectionTool {
  public static final String SHORT_NAME = "GroovyUnusedDeclaration";

  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return "Declaration redundancy";
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return "Unused declaration";
  }
}
