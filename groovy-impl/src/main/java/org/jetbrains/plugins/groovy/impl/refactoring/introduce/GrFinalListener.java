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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce;

import com.intellij.java.language.psi.PsiModifier;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.completion.lookup.LookupEx;
import consulo.language.editor.completion.lookup.LookupManager;
import consulo.language.psi.PsiDocumentManager;
import consulo.logging.Logger;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author Max Medvedev
 */
public class GrFinalListener {
  private final Editor myEditor;
  private static final Logger LOG = Logger.getInstance(GrFinalListener.class);

  public GrFinalListener(Editor editor) {
    myEditor = editor;
  }

  public void perform(boolean generateFinal, GrVariable variable) {
    perform(generateFinal, PsiModifier.FINAL, variable);
  }

  public void perform(final boolean generateFinal, final String modifier, final GrVariable variable) {
    final Document document = myEditor.getDocument();
    LOG.assertTrue(variable != null);
    final GrModifierList modifierList = variable.getModifierList();
    LOG.assertTrue(modifierList != null);
    final int textOffset = modifierList.getTextOffset();

    Runnable runnable = new Runnable() {
      public void run() {
        if (generateFinal) {
          GrTypeElement typeElement = variable.getTypeElementGroovy();
          int typeOffset = typeElement != null ? typeElement.getTextOffset() : textOffset;
          document.insertString(typeOffset, modifier + " ");
        }
        else {
          int idx = modifierList.getText().indexOf(modifier);
          document.deleteString(textOffset + idx, textOffset + idx + modifier.length() + 1);
        }
      }
    };
    LookupEx lookup = LookupManager.getActiveLookup(myEditor);
    if (lookup != null) {
      lookup.performGuardedChange(runnable);
    } else {
      runnable.run();
    }
    PsiDocumentManager.getInstance(variable.getProject()).commitDocument(document);
  }
}

