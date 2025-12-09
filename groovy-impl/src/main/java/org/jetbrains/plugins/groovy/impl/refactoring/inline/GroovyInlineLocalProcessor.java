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
package org.jetbrains.plugins.groovy.impl.refactoring.inline;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.BaseUsageViewDescriptor;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;

/**
 * @author Max Medvedev
 */
public class GroovyInlineLocalProcessor extends BaseRefactoringProcessor {
    private final InlineLocalVarSettings mySettings;
    private final GrVariable myLocal;

    public GroovyInlineLocalProcessor(Project project, InlineLocalVarSettings settings, GrVariable local) {
        super(project);
        this.mySettings = settings;
        this.myLocal = local;
    }

    @Nonnull
    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(@Nonnull UsageInfo[] usages) {
        return new BaseUsageViewDescriptor(myLocal);
    }

    @Override
    @RequiredUIAccess
    protected boolean preprocessUsages(SimpleReference<UsageInfo[]> refUsages) {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        UsageInfo[] usages = refUsages.get();
        for (UsageInfo usage : usages) {
            collectConflicts(usage.getReference(), conflicts);
        }

        return showConflicts(conflicts, usages);
    }

    @Override
    protected boolean isPreviewUsages(UsageInfo[] usages) {
        for (UsageInfo usage : usages) {
            if (usage instanceof ClosureUsage) {
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private void collectConflicts(PsiReference reference, MultiMap<PsiElement, LocalizeValue> conflicts) {
        GrExpression expr = (GrExpression)reference.getElement();
        if (PsiUtil.isAccessedForWriting(expr)) {
            conflicts.putValue(expr, GroovyRefactoringLocalize.variableIsAccessedForWriting(myLocal.getName()));
        }
    }

    @Nonnull
    @Override
    protected UsageInfo[] findUsages() {
        Instruction[] controlFlow = mySettings.getFlow();
        ArrayList<BitSet> writes = ControlFlowUtils.inferWriteAccessMap(controlFlow, myLocal);

        ArrayList<UsageInfo> toInline = new ArrayList<>();
        collectRefs(myLocal, controlFlow, writes, mySettings.getWriteInstructionNumber(), toInline);

        return toInline.toArray(new UsageInfo[toInline.size()]);
    }

    /**
     * ClosureUsage represents usage of local var inside closure
     */
    private static class ClosureUsage extends UsageInfo {
        private ClosureUsage(@Nonnull PsiReference reference) {
            super(reference);
        }
    }

    private static void collectRefs(
        GrVariable variable,
        Instruction[] flow,
        ArrayList<BitSet> writes,
        int writeInstructionNumber,
        ArrayList<UsageInfo> toInline
    ) {
        for (Instruction instruction : flow) {
            PsiElement element = instruction.getElement();
            if (instruction instanceof ReadWriteVariableInstruction readWriteVarInsn) {
                if (readWriteVarInsn.isWrite()) {
                    continue;
                }

                if (element instanceof GrVariable && element != variable) {
                    continue;
                }
                if (!(element instanceof GrReferenceExpression)) {
                    continue;
                }

                GrReferenceExpression ref = (GrReferenceExpression)element;
                if (ref.isQualified() || ref.resolve() != variable) {
                    continue;
                }

                BitSet prev = writes.get(instruction.num());
                if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber)) {
                    toInline.add(new UsageInfo(ref));
                }
                else if (writeInstructionNumber == -1 && prev.cardinality() == 0) {
                    toInline.add(new ClosureUsage(ref));
                }
            }
            else if (element instanceof GrClosableBlock closableBlock) {
                BitSet prev = writes.get(instruction.num());
                if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber) ||
                    writeInstructionNumber == -1 && prev.cardinality() == 0) {
                    Instruction[] closureFlow = closableBlock.getControlFlow();
                    collectRefs(variable, closureFlow, ControlFlowUtils.inferWriteAccessMap(closureFlow, variable), -1, toInline);
                }
            }
            else if (element instanceof GrAnonymousClassDefinition anonymousClassDef) {
                BitSet prev = writes.get(instruction.num());
                if (writeInstructionNumber >= 0 && prev.cardinality() == 1 && prev.get(writeInstructionNumber)
                    || writeInstructionNumber == -1 && prev.cardinality() == 0) {
                    anonymousClassDef.acceptChildren(new GroovyRecursiveElementVisitor() {
                        @Override
                        public void visitField(GrField field) {
                            GrExpression initializer = field.getInitializerGroovy();
                            if (initializer != null) {
                                Instruction[] flow = new ControlFlowBuilder(field.getProject()).buildControlFlow(initializer);
                                collectRefs(variable, flow, ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
                            }
                        }

                        @Override
                        public void visitMethod(GrMethod method) {
                            GrOpenBlock block = method.getBlock();
                            if (block != null) {
                                Instruction[] flow = block.getControlFlow();
                                collectRefs(variable, flow, ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
                            }
                        }

                        @Override
                        public void visitClassInitializer(GrClassInitializer initializer) {
                            GrOpenBlock block = initializer.getBlock();
                            Instruction[] flow = block.getControlFlow();
                            collectRefs(variable, flow, ControlFlowUtils.inferWriteAccessMap(flow, variable), -1, toInline);
                        }
                    });
                }
            }
        }
    }

    @Override
    @RequiredWriteAction
    protected void performRefactoring(UsageInfo[] usages) {
        CommonRefactoringUtil.sortDepthFirstRightLeftOrder(usages);

        GrExpression initializer = mySettings.getInitializer();

        GrExpression initializerToUse = GrIntroduceHandlerBase.insertExplicitCastIfNeeded(myLocal, mySettings.getInitializer());

        for (UsageInfo usage : usages) {
            GrVariableInliner.inlineReference(usage, myLocal, initializerToUse);
        }

        PsiElement initializerParent = initializer.getParent();

        if (initializerParent instanceof GrAssignmentExpression) {
            initializerParent.delete();
            return;
        }

        if (initializerParent instanceof GrVariable) {
            Collection<PsiReference> all = ReferencesSearch.search(myLocal).findAll();
            if (all.size() > 0) {
                initializer.delete();
                return;
            }
        }

        PsiElement owner = myLocal.getParent().getParent();
        if (owner instanceof GrVariableDeclarationOwner varDeclarationOwner) {
            varDeclarationOwner.removeVariable(myLocal);
        }
        else {
            myLocal.delete();
        }
    }

    @Nonnull
    @Override
    protected String getCommandName() {
        return RefactoringLocalize.inlineCommand(myLocal.getName()).get();
    }
}