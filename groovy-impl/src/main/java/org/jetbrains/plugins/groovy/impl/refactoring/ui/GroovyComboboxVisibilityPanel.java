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
package org.jetbrains.plugins.groovy.impl.refactoring.ui;

import consulo.ide.impl.idea.refactoring.ui.ComboBoxVisibilityPanel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

import static com.intellij.java.language.psi.PsiModifier.*;
import static com.intellij.java.language.util.VisibilityUtil.toPresentableText;

/**
 * @author Max Medvedev
 */
public class GroovyComboboxVisibilityPanel extends ComboBoxVisibilityPanel<String> {
  private static final String[] MODIFIERS = {PRIVATE, PROTECTED, PUBLIC, GrModifier.DEF};

  private static final String[] PRESENTABLE_NAMES = {
    toPresentableText(PRIVATE),
    toPresentableText(PROTECTED),
    toPresentableText(PUBLIC),
    GrModifier.DEF
  };

  public GroovyComboboxVisibilityPanel() {
    super(MODIFIERS, PRESENTABLE_NAMES);
  }
}
