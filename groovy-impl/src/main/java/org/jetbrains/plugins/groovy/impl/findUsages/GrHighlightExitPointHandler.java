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
package org.jetbrains.plugins.groovy.impl.findUsages;

import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.editor.highlight.usage.HighlightUsagesHandlerBase;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Max Medvedev
 */
public class GrHighlightExitPointHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  protected GrHighlightExitPointHandler(Editor editor, PsiFile file, PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(List<PsiElement> targets, Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.accept(targets);
  }

  @Override
  public void computeUsages(List<PsiElement> targets) {
    FeatureUsageTracker.getInstance()
                       .triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN);

    PsiElement parent = myTarget.getParent();
    if (!(parent instanceof GrReturnStatement) && !(parent instanceof GrThrowStatement)) return;

    GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(parent);
    ControlFlowUtils.visitAllExitPoints(flowOwner, new ControlFlowUtils.ExitPointVisitor() {
      @Override
      public boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue) {
        PsiElement returnElement = instruction.getElement();
        if (returnElement != null && isCorrectReturn(returnElement)) {
          TextRange range = returnElement.getTextRange();
          myReadUsages.add(range);
        }
        return true;
      }
    });
  }

  private static boolean isCorrectReturn(@Nullable PsiElement e) {
    return e instanceof GrReturnStatement || e instanceof GrThrowStatement || e instanceof GrExpression;
  }
}
