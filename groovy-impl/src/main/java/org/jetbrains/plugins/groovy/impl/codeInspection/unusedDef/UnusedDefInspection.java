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
package org.jetbrains.plugins.groovy.impl.codeInspection.unusedDef;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.Processor;
import consulo.document.util.TextRange;
import consulo.ide.impl.idea.diagnostic.LogMessageEx;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.logging.Logger;
import consulo.util.collection.primitive.ints.IntSet;
import consulo.util.collection.primitive.ints.IntSets;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyLocalInspectionBase;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 & @author ven
 */
@ExtensionImpl
public class UnusedDefInspection extends GroovyLocalInspectionBase {
  private static final Logger LOG = Logger.getInstance(UnusedDefInspection.class);

  @Nls
  @Nonnull
  public String getGroupDisplayName() {
    return GroovyInspectionBundle.message("groovy.dfa.issues");
  }

  @Nls
  @Nonnull
  public String getDisplayName() {
    return GroovyInspectionBundle.message("unused.assignment");
  }

  @NonNls
  @Nonnull
  public String getShortName() {
    return "GroovyUnusedAssignment";
  }


  protected void check(final GrControlFlowOwner owner, final ProblemsHolder problemsHolder) {
    final Instruction[] flow = owner.getControlFlow();
    final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow);
    final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
    final DFAEngine<DefinitionMap> engine = new DFAEngine<DefinitionMap>(flow, dfaInstance, lattice);
    final List<DefinitionMap> dfaResult = engine.performDFAWithTimeout();
    if (dfaResult == null) {
      return;
    }

    final IntSet unusedDefs = IntSets.newHashSet();
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction) instruction).isWrite()) {
        unusedDefs.add(instruction.num());
      }
    }

    for (int i = 0; i < dfaResult.size(); i++) {
      final Instruction instruction = flow[i];
      if (instruction instanceof ReadWriteVariableInstruction) {
        final ReadWriteVariableInstruction varInst = (ReadWriteVariableInstruction) instruction;
        if (!varInst.isWrite()) {
          final String varName = varInst.getVariableName();
          DefinitionMap e = dfaResult.get(i);
          e.forEachValue(new Consumer<IntSet>() {
            public void accept(IntSet reaching) {
              reaching.forEach(new IntConsumer() {
                public void accept(int defNum) {
                  final String defName = ((ReadWriteVariableInstruction) flow[defNum]).getVariableName();
                  if (varName.equals(defName)) {
                    unusedDefs.remove(defNum);
                  }
                }
              });
            }
          });
        }
      }
    }

    final Set<PsiElement> checked = new HashSet<>();

    unusedDefs.forEach(new IntConsumer() {
      public void accept(int num) {
        final ReadWriteVariableInstruction instruction = (ReadWriteVariableInstruction)flow[num];
        final PsiElement element = instruction.getElement();
        process(element, checked, problemsHolder, GroovyInspectionBundle.message("unused.assignment.tooltip"));
      }
    });

    owner.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitVariable(GrVariable variable) {
        if (checked.contains(variable) || variable.getInitializerGroovy() != null) return;

        if (ReferencesSearch.search(variable).findFirst() == null) {
          process(variable, checked, problemsHolder, GroovyInspectionBundle.message("unused.variable"));
        }
      }
    });
  }

  private static void process(@Nullable PsiElement element, Set<PsiElement> checked, ProblemsHolder problemsHolder, final String message) {
    if (element == null) return;
    if (!checked.add(element)) return;
    if (isLocalAssignment(element) && isUsedInTopLevelFlowOnly(element) && !isIncOrDec(element)) {
      PsiElement toHighlight = getHighlightElement(element);
      problemsHolder.registerProblem(toHighlight, message, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
    }
  }

  private static PsiElement getHighlightElement(PsiElement element) {
    PsiElement toHighlight = null;
    if (element instanceof GrReferenceExpression) {
      PsiElement parent = element.getParent();
      if (parent instanceof GrAssignmentExpression) {
        toHighlight = ((GrAssignmentExpression)parent).getLValue();
      }
      if (parent instanceof GrUnaryExpression && ((GrUnaryExpression)parent).isPostfix()) {
        toHighlight = parent;
      }
    }
    else if (element instanceof GrVariable) {
      toHighlight = ((GrVariable)element).getNameIdentifierGroovy();
    }
    if (toHighlight == null) toHighlight = element;
    return toHighlight;
  }

  private static boolean isIncOrDec(PsiElement element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof GrUnaryExpression)) return false;

    IElementType type = ((GrUnaryExpression)parent).getOperationTokenType();
    return type == GroovyTokenTypes.mINC || type == GroovyTokenTypes.mDEC;
  }

  private static boolean isUsedInTopLevelFlowOnly(PsiElement element) {
    GrVariable var = null;
    if (element instanceof GrVariable) {
      var = (GrVariable)element;
    }
    else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)element).resolve();
      if (resolved instanceof GrVariable) var = (GrVariable)resolved;
    }

    if (var != null) {
      final GroovyPsiElement scope = ControlFlowUtils.findControlFlowOwner(var);
      if (scope == null) {
        PsiFile file = var.getContainingFile();
        if (file == null) {
          LogMessageEx.error(LOG, "no file??? var of type" + var.getClass().getCanonicalName());
          return false;
        }
        else {
          TextRange range = var.getTextRange();
          LogMessageEx.error(LOG, "var: " + var.getName() + ", offset:" + (range != null ? range.getStartOffset() : -1), file.getText());
          return false;
        }
      }

      return ReferencesSearch.search(var, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference ref) {
          return ControlFlowUtils.findControlFlowOwner(ref.getElement()) == scope;
        }
      });
    }

    return true;
  }


  private static boolean isLocalAssignment(PsiElement element) {
    if (element instanceof GrVariable) {
      return isLocalVariable((GrVariable)element, false);
    }
    else if (element instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)element).resolve();
      return resolved instanceof GrVariable && isLocalVariable((GrVariable)resolved, true);
    }

    return false;
  }

  private static boolean isLocalVariable(GrVariable var, boolean parametersAllowed) {
    return !(var instanceof GrField || var instanceof GrParameter && !parametersAllowed);
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
