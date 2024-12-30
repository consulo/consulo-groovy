/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.actions;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.editor.HandlerUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Max Medvedev
 */
public class DumpGroovyControlFlowAction extends AnAction implements DumbAware {
  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor == null) {
      return;
    }

    final PsiFile psiFile = HandlerUtils.getPsiFile(editor, e.getDataContext());
    if (!(psiFile instanceof GroovyFile)) {
      return;
    }

    int offset = editor.getCaretModel().getOffset();

    final List<GrControlFlowOwner> controlFlowOwners = collectControlFlowOwners(psiFile, editor, offset);
    if (controlFlowOwners.size() == 0) {
      return;
    }
    if (controlFlowOwners.size() == 1) {
      passInner(controlFlowOwners.get(0));
    }
    else {
      IntroduceTargetChooser.showChooser(editor, controlFlowOwners, new Consumer<GrControlFlowOwner>() {
        @Override
        public void accept(GrControlFlowOwner grExpression) {
          passInner(grExpression);
        }
      }, PsiElement::getText);
    }
  }

  @RequiredReadAction
  private static List<GrControlFlowOwner> collectControlFlowOwners(final PsiFile file, final Editor editor, final int offset) {
    final PsiElement elementAtCaret = file.findElementAt(offset);
    final List<GrControlFlowOwner> result = new ArrayList<GrControlFlowOwner>();

    for (GrControlFlowOwner owner = ControlFlowUtils.findControlFlowOwner(elementAtCaret); owner != null;
         owner = ControlFlowUtils.findControlFlowOwner(owner)) {
      result.add(owner);
    }
    return result;
  }

  @RequiredReadAction
  private static void passInner(GrControlFlowOwner owner) {
    System.out.println(owner.getText());
    System.out.println(ControlFlowUtils.dumpControlFlow(owner.getControlFlow()));
  }
}
