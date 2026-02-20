/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.inline.InlineActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.editor.refactoring.util.RefactoringMessageDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;

import java.util.ArrayList;
import java.util.BitSet;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyInlineLocalHandler extends InlineActionHandler {
  private static final Logger LOG = Logger.getInstance(GroovyInlineLocalHandler.class);
  public static final String INLINE_VARIABLE = RefactoringBundle.message("inline.variable.title");

  @Override
  public boolean isEnabledForLanguage(Language l) {
    return GroovyLanguage.INSTANCE == l;
  }

  @Override
  public boolean canInlineElement(PsiElement element) {
    return PsiUtil.isLocalVariable(element);
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    invoke(project, editor, (GrVariable)element);
  }

  public static void invoke(Project project, Editor editor, GrVariable local) {
    PsiReference invocationReference = editor != null ? TargetElementUtil.findReference(editor) : null;

    InlineLocalVarSettings localVarSettings = createSettings(local, editor, invocationReference != null);
    if (localVarSettings == null) {
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, local)) {
      return;
    }

    GroovyInlineLocalProcessor processor = new GroovyInlineLocalProcessor(project, localVarSettings, local);
    processor.setPrepareSuccessfulSwingThreadCallback(new Runnable() {
      @Override
      public void run() {
        //do nothing
      }
    });
    processor.run();
  }


  /**
   * Returns Settings object for referenced definition in case of local variable
   */
  @Nullable
  private static InlineLocalVarSettings createSettings(final GrVariable variable,
                                                       Editor editor,
                                                       boolean invokedOnReference) {
    String localName = variable.getName();
    Project project = variable.getProject();

    GrExpression initializer = null;
    Instruction writeInstr = null;
    Instruction[] flow = null;

    //search for initializer to inline
    if (invokedOnReference) {
      LOG.assertTrue(editor != null, "null editor but invokedOnReference==true");
      PsiReference ref = TargetElementUtil.findReference(editor);
      LOG.assertTrue(ref != null);

      PsiElement cur = ref.getElement();
      if (cur instanceof GrReferenceExpression) {

        GrControlFlowOwner controlFlowOwner;
        do {
          controlFlowOwner = ControlFlowUtils.findControlFlowOwner(cur);
          if (controlFlowOwner == null) {
            break;
          }

          flow = controlFlowOwner.getControlFlow();

          ArrayList<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(flow, variable);
          PsiElement finalCur = cur;
          Instruction instruction = ControlFlowUtils.findInstruction(finalCur, flow);

          LOG.assertTrue(instruction != null);
          BitSet prev = writes.get(instruction.num());
          if (prev.cardinality() == 1) {
            writeInstr = flow[prev.nextSetBit(0)];
            PsiElement element = writeInstr.getElement();
            if (element instanceof GrVariable) {
              initializer = ((GrVariable)element).getInitializerGroovy();
            }
            else if (element instanceof GrReferenceExpression) {
              initializer = TypeInferenceHelper.getInitializerFor((GrReferenceExpression)element);
            }
          }

          PsiElement old_cur = cur;
          if (controlFlowOwner instanceof GrClosableBlock) {
            cur = controlFlowOwner;
          }
          else {
            PsiElement parent = controlFlowOwner.getParent();
            if (parent instanceof GrMember) {
              cur = ((GrMember)parent).getContainingClass();
            }
          }
          if (cur == old_cur) {
            break;
          }
        }
        while (initializer == null);
      }
    }
    else {
      flow = ControlFlowUtils.findControlFlowOwner(variable).getControlFlow();
      initializer = variable.getInitializerGroovy();
      writeInstr = ContainerUtil.find(flow, new Condition<Instruction>() {
        @Override
        public boolean value(Instruction instruction) {
          return instruction.getElement() == variable;
        }
      });
    }

    if (initializer == null || writeInstr == null) {
      String message = GroovyRefactoringBundle.message("cannot.find.a.single.definition.to.inline.local.var");
      CommonRefactoringUtil.showErrorHint(variable.getProject(), editor, message, INLINE_VARIABLE,
																					HelpID.INLINE_VARIABLE);
      return null;
    }

    int writeInstructionNumber = writeInstr.num();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return new InlineLocalVarSettings(initializer, writeInstructionNumber, flow);
    }

    String question = GroovyRefactoringBundle.message("inline.local.variable.prompt.0.1", localName);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(INLINE_VARIABLE, question,
                                                                   HelpID.INLINE_VARIABLE, "OptionPane.questionIcon", true, project);
    if (dialog.showAndGet()) {
      return new InlineLocalVarSettings(initializer, writeInstructionNumber, flow);
    }

    return null;
  }
}
