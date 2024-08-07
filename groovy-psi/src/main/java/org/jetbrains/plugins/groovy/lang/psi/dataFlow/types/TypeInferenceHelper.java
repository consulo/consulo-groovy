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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.function.Condition;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.InstanceOfInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsDfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsSemilattice;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.InferenceContext;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author ven
 */
@SuppressWarnings("UtilityClassWithoutPrivateConstructor")
public class TypeInferenceHelper {
  private static final Logger LOG = Logger.getInstance(TypeInferenceHelper.class);
  private static final ThreadLocal<InferenceContext> ourInferenceContext = new ThreadLocal<InferenceContext>();

  private static <T> T doInference(@Nonnull Map<String, PsiType> bindings, @Nonnull Supplier<T> computation) {
    InferenceContext old = ourInferenceContext.get();
    ourInferenceContext.set(new InferenceContext.PartialContext(bindings));
    try {
      return computation.get();
    }
    finally {
      ourInferenceContext.set(old);
    }
  }

  public static InferenceContext getCurrentContext() {
    InferenceContext context = ourInferenceContext.get();
    return context != null ? context : InferenceContext.TOP_CONTEXT;
  }

  @Nullable
  public static PsiType getInferredType(@Nonnull final GrReferenceExpression refExpr) {
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(refExpr);
    if (scope == null) {
      return null;
    }

    final String referenceName = refExpr.getReferenceName();
    if (referenceName == null) {
      return null;
    }

    final ReadWriteVariableInstruction rwInstruction = ControlFlowUtils.findRWInstruction(refExpr,
                                                                                          scope.getControlFlow());
    if (rwInstruction == null) {
      return null;
    }

    return getInferenceCache(scope).getInferredType(referenceName, rwInstruction);
  }

  @Nullable
  public static PsiType getInferredType(@Nonnull PsiElement place, @Nonnull String variableName) {
    final GrControlFlowOwner scope = ControlFlowUtils.findControlFlowOwner(place);
    if (scope == null) {
      return null;
    }

    final Instruction nearest = ControlFlowUtils.findNearestInstruction(place, scope.getControlFlow());
    if (nearest == null) {
      return null;
    }
    return getInferenceCache(scope).getInferredType(variableName, nearest);
  }

  @Nonnull
  private static InferenceCache getInferenceCache(@Nonnull final GrControlFlowOwner scope) {
    return LanguageCachedValueUtil.getCachedValue(scope, new CachedValueProvider<InferenceCache>() {
      @Nullable
      @Override
      public Result<InferenceCache> compute() {
        return Result.create(new InferenceCache(scope), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public static boolean isTooComplexTooAnalyze(@Nonnull GrControlFlowOwner scope) {
    return getDefUseMaps(scope) == null;
  }

  @Nullable
  private static DFAType getInferredType(@Nonnull String varName,
                                         @Nonnull Instruction instruction,
                                         @Nonnull Instruction[] flow,
                                         @Nonnull GrControlFlowOwner scope,
                                         Set<MixinTypeInstruction> trace) {
    final Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> pair = getDefUseMaps(scope);
    if (pair == null) {
      return null;
    }

    final int varIndex = pair.first.getVarIndex(varName);
    final DefinitionMap allDefs = pair.second.get(instruction.num());
    final int[] varDefs = allDefs.getDefinitions(varIndex);
    if (varDefs == null) {
      return null;
    }

    DFAType result = null;
    for (int defIndex : varDefs) {
      DFAType defType = getDefinitionType(flow[defIndex], flow, scope, trace);

      if (defType != null) {
        defType = defType.negate(instruction);
      }

      if (defType != null) {
        result = result == null ? defType : DFAType.create(defType, result, scope.getManager());
      }
    }
    return result;
  }

  @Nullable
  private static Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> getDefUseMaps(@Nonnull final
                                                                                         GrControlFlowOwner scope) {
    return LanguageCachedValueUtil.getCachedValue(scope, new CachedValueProvider<Pair<ReachingDefinitionsDfaInstance,
      List<DefinitionMap>>>() {
      @Override
      public Result<Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>>> compute() {
        final Instruction[] flow = scope.getControlFlow();
        final ReachingDefinitionsDfaInstance dfaInstance = new ReachingDefinitionsDfaInstance(flow) {
          @Override
          public void fun(DefinitionMap m, Instruction instruction) {
            if (instruction instanceof InstanceOfInstruction) {
              final InstanceOfInstruction instanceOfInstruction = (InstanceOfInstruction)instruction;
              ReadWriteVariableInstruction i = instanceOfInstruction.getInstructionToMixin(flow);
              if (i != null) {
                int varIndex = getVarIndex(i.getVariableName());
                if (varIndex >= 0) {
                  m.registerDef(instruction, varIndex);
                }
              }
            }
            else if (instruction instanceof ArgumentInstruction) {
              String variableName = ((ArgumentInstruction)instruction).getVariableName();
              if (variableName != null) {
                m.registerDef(instruction, getVarIndex(variableName));
              }
            }
            else {
              super.fun(m, instruction);
            }
          }
        };
        final ReachingDefinitionsSemilattice lattice = new ReachingDefinitionsSemilattice();
        final DFAEngine<DefinitionMap> engine = new DFAEngine<DefinitionMap>(flow, dfaInstance, lattice);
        final List<DefinitionMap> dfaResult = engine.performDFAWithTimeout();
        Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> result = dfaResult == null ? null : Pair
          .create(dfaInstance, dfaResult);
        return Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static DFAType getDefinitionType(Instruction instruction,
                                           Instruction[] flow,
                                           GrControlFlowOwner scope,
                                           Set<MixinTypeInstruction> trace) {
    if (instruction instanceof ReadWriteVariableInstruction && ((ReadWriteVariableInstruction)instruction)
      .isWrite()) {
      final PsiElement element = instruction.getElement();
      if (element != null) {
        return DFAType.create(TypesUtil.boxPrimitiveType(getInitializerType(element), scope.getManager(),
                                                         scope.getResolveScope()));
      }
    }
    if (instruction instanceof MixinTypeInstruction) {
      return mixinType((MixinTypeInstruction)instruction, flow, scope, trace);
    }
    return null;
  }

  @Nullable
  private static DFAType mixinType(final MixinTypeInstruction instruction,
                                   final Instruction[] flow,
                                   final GrControlFlowOwner scope,
                                   Set<MixinTypeInstruction> trace) {
    if (!trace.add(instruction)) {
      return null;
    }

    String varName = instruction.getVariableName();
    if (varName == null) {
      return null;
    }
    ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(flow);
    if (originalInstr == null) {
      LOG.error(scope.getContainingFile().getName() + ":" + scope.getText());
    }

    DFAType original = getInferredType(varName, originalInstr, flow, scope, trace);
    final PsiType mixin = instruction.inferMixinType();
    if (mixin == null) {
      return original;
    }
    if (original == null) {
      original = DFAType.create(null);
    }
    original.addMixin(mixin, instruction.getConditionInstruction());
    trace.remove(instruction);
    return original;
  }


  @Nullable
  public static PsiType getInitializerType(final PsiElement element) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).getQualifierExpression() ==
      null) {
      return getInitializerTypeFor(element);
    }

    if (element instanceof GrVariable) {
      return ((GrVariable)element).getTypeGroovy();
    }

    return null;
  }

  @Nullable
  public static PsiType getInitializerTypeFor(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof GrAssignmentExpression) {
      if (element instanceof GrIndexProperty) {
        final GrExpression rvalue = ((GrAssignmentExpression)parent).getRValue();
        return rvalue != null ? rvalue.getType() : null; //don't try to infer assignment type in case of index
        // property because of infinite recursion (example: a[2]+=4)
      }
      return ((GrAssignmentExpression)parent).getType();
    }

    if (parent instanceof GrTupleExpression) {
      GrTupleExpression list = (GrTupleExpression)parent;
      if (list.getParent() instanceof GrAssignmentExpression) { // multiple assignment
        final GrExpression rValue = ((GrAssignmentExpression)list.getParent()).getRValue();
        int idx = list.indexOf(element);
        if (idx >= 0 && rValue != null) {
          PsiType rType = rValue.getType();
          if (rType instanceof GrTupleType) {
            PsiType[] componentTypes = ((GrTupleType)rType).getComponentTypes();
            if (idx < componentTypes.length) {
              return componentTypes[idx];
            }
            return null;
          }
          return PsiUtil.extractIterableTypeParameter(rType, false);
        }
      }
    }
    if (parent instanceof GrUnaryExpression && TokenSets.POSTFIX_UNARY_OP_SET.contains(((GrUnaryExpression)parent)
                                                                                         .getOperationTokenType())) {
      return ((GrUnaryExpression)parent).getType();
    }

    return null;
  }

  @Nullable
  public static GrExpression getInitializerFor(GrExpression lValue) {
    final PsiElement parent = lValue.getParent();
    if (parent instanceof GrAssignmentExpression) {
      return ((GrAssignmentExpression)parent).getRValue();
    }
    if (parent instanceof GrTupleExpression) {
      final int i = ((GrTupleExpression)parent).indexOf(lValue);
      final PsiElement pparent = parent.getParent();
      org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.LOG.assertTrue(pparent instanceof
                                                                          GrAssignmentExpression);

      final GrExpression rValue = ((GrAssignmentExpression)pparent).getRValue();
      if (rValue instanceof GrListOrMap && !((GrListOrMap)rValue).isMap()) {
        final GrExpression[] initializers = ((GrListOrMap)rValue).getInitializers();
        if (initializers.length < i) {
          return initializers[i];
        }
      }
    }

    return null;
  }

  static class TypeDfaInstance implements DfaInstance<TypeDfaState> {
    private final GrControlFlowOwner myScope;
    private final Instruction[] myFlow;
    private final Set<Instruction> myInteresting;
    private final InferenceCache myCache;

    TypeDfaInstance(@Nonnull GrControlFlowOwner scope,
                    @Nonnull Instruction[] flow,
                    @Nonnull Set<Instruction> interesting,
                    @Nonnull InferenceCache cache) {
      myScope = scope;
      myFlow = flow;
      myInteresting = interesting;
      myCache = cache;
    }

    @Override
    public void fun(final TypeDfaState state, final Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction) {
        handleVariableWrite(state, (ReadWriteVariableInstruction)instruction);
      }
      else if (instruction instanceof MixinTypeInstruction) {
        handleMixin(state, (MixinTypeInstruction)instruction);
      }
    }

    private void handleMixin(@Nonnull final TypeDfaState state, @Nonnull final MixinTypeInstruction instruction) {
      final String varName = instruction.getVariableName();
      if (varName == null) {
        return;
      }

      updateVariableType(state, instruction, varName, new Supplier<DFAType>() {
        @Override
        public DFAType get() {
          ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
          assert originalInstr != null && !originalInstr.isWrite();

          DFAType original = state.getVariableType(varName);
          if (original == null) {
            original = DFAType.create(null);
          }
          original = original.negate(originalInstr);
          original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
          return original;
        }
      });
    }

    private void handleVariableWrite(TypeDfaState state, ReadWriteVariableInstruction instruction) {
      final PsiElement element = instruction.getElement();
      if (element != null && instruction.isWrite()) {
        updateVariableType(state, instruction, instruction.getVariableName(), new Computable<DFAType>() {
          @Override
          public DFAType compute() {
            return DFAType.create(TypesUtil.boxPrimitiveType(getInitializerType(element),
                                                             myScope.getManager(), myScope.getResolveScope()));
          }
        });
      }
    }

    private void updateVariableType(@Nonnull TypeDfaState state,
                                    @Nonnull Instruction instruction,
                                    @Nonnull String variableName,
                                    @Nonnull Supplier<DFAType> computation) {
      if (!myInteresting.contains(instruction)) {
        state.removeBinding(variableName);
        return;
      }

      DFAType type = myCache.getCachedInferredType(variableName, instruction);
      if (type == null) {
        type = doInference(state.getBindings(instruction), computation);
      }
      state.putType(variableName, type);
    }

    @Override
    @Nonnull
    public TypeDfaState initial() {
      return new TypeDfaState();
    }

    @Override
    public boolean isForward() {
      return true;
    }

  }

  private static class InferenceCache {
    final GrControlFlowOwner scope;
    final Instruction[] flow;
    final AtomicReference<List<TypeDfaState>> varTypes;
    final Set<Instruction> tooComplex = ContainerUtil.newConcurrentSet();

    InferenceCache(final GrControlFlowOwner scope) {
      this.scope = scope;
      this.flow = scope.getControlFlow();
      List<TypeDfaState> noTypes = new ArrayList<TypeDfaState>();
      //noinspection ForLoopReplaceableByForEach
      for (int i = 0; i < flow.length; i++) {
        noTypes.add(new TypeDfaState());
      }
      varTypes = new AtomicReference<List<TypeDfaState>>(noTypes);
    }

    @Nullable
    private PsiType getInferredType(@Nonnull String variableName, @Nonnull Instruction instruction) {
      if (tooComplex.contains(instruction)) {
        return null;
      }

      TypeDfaState cache = varTypes.get().get(instruction.num());
      if (!cache.containsVariable(variableName)) {
        Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> defUse = getDefUseMaps(scope);
        if (defUse == null) {
          tooComplex.add(instruction);
          return null;
        }

        Set<Instruction> interesting = collectRequiredInstructions(instruction, variableName, defUse);
        List<TypeDfaState> dfaResult = performTypeDfa(scope, flow, interesting);
        if (dfaResult == null) {
          tooComplex.addAll(interesting);
        }
        else {
          cacheDfaResult(dfaResult);
        }
      }
      DFAType dfaType = getCachedInferredType(variableName, instruction);
      return dfaType == null ? null : dfaType.getResultType();
    }

    @Nullable
    private List<TypeDfaState> performTypeDfa(@Nonnull GrControlFlowOwner owner,
                                              @Nonnull Instruction[] flow,
                                              @Nonnull Set<Instruction> interesting) {
      final TypeDfaInstance dfaInstance = new TypeDfaInstance(owner, flow, interesting, this);
      final TypesSemilattice semilattice = new TypesSemilattice(owner.getManager());
      return new DFAEngine<TypeDfaState>(flow, dfaInstance, semilattice).performDFAWithTimeout();
    }

    @Nullable
    DFAType getCachedInferredType(@Nonnull String variableName, @Nonnull Instruction instruction) {
      DFAType dfaType = varTypes.get().get(instruction.num()).getVariableType(variableName);
      return dfaType == null ? null : dfaType.negate(instruction);
    }

    private Set<Instruction> collectRequiredInstructions(@Nonnull Instruction instruction,
                                                         @Nonnull String variableName,
                                                         @Nonnull Pair<ReachingDefinitionsDfaInstance, List<DefinitionMap>> defUse) {
      Set<Instruction> interesting = new HashSet<>(Set.of(instruction));
      LinkedList<Pair<Instruction, String>> queue = new LinkedList<>();
      queue.add(Pair.create(instruction, variableName));
      while (!queue.isEmpty()) {
        Pair<Instruction, String> pair = queue.removeFirst();
        for (Pair<Instruction, String> dep : findDependencies(defUse, pair.first, pair.second)) {
          if (interesting.add(dep.first)) {
            queue.addLast(dep);
          }
        }
      }

      return interesting;
    }

    @Nonnull
    private Set<Pair<Instruction, String>> findDependencies(@Nonnull Pair<ReachingDefinitionsDfaInstance,
      List<DefinitionMap>> defUse,
                                                            @Nonnull Instruction insn,
                                                            @Nonnull String varName) {
      DefinitionMap definitionMap = defUse.second.get(insn.num());
      int varIndex = defUse.first.getVarIndex(varName);
      int[] definitions = definitionMap.getDefinitions(varIndex);
      if (definitions == null) {
        return Collections.emptySet();
      }

      LinkedHashSet<Pair<Instruction, String>> pairs = new LinkedHashSet<>();
      for (int defIndex : definitions) {
        Instruction write = flow[defIndex];
        pairs.add(Pair.create(write, varName));
        PsiElement statement = findDependencyScope(write.getElement());
        if (statement != null) {
          pairs.addAll(findAllInstructionsInside(statement));
        }
      }
      return pairs;
    }

    @Nonnull
    private List<Pair<Instruction, String>> findAllInstructionsInside(@Nonnull PsiElement scope) {
      final List<Pair<Instruction, String>> result = ContainerUtil.newArrayList();
      scope.accept(new PsiRecursiveElementWalkingVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (element instanceof GrReferenceExpression && !((GrReferenceExpression)element).isQualified()) {
            String varName = ((GrReferenceExpression)element).getReferenceName();
            if (varName != null) {
              for (Instruction dependency : ControlFlowUtils.findAllInstructions(element, flow)) {
                result.add(Pair.create(dependency, varName));
              }
            }
          }
          super.visitElement(element);
        }
      });
      return result;
    }

    @Nullable
    private static PsiElement findDependencyScope(@Nullable PsiElement element) {
      return PsiTreeUtil.findFirstParent(element, new Condition<PsiElement>() {
        @Override
        public boolean value(PsiElement element) {
          return org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isExpressionStatement(element) || !
            (element.getParent() instanceof GrExpression);
        }
      });
    }

    private void cacheDfaResult(@Nonnull List<TypeDfaState> dfaResult) {
      while (true) {
        List<TypeDfaState> oldTypes = varTypes.get();
        if (varTypes.compareAndSet(oldTypes, addDfaResult(dfaResult, oldTypes))) {
          return;
        }
      }
    }

    @Nonnull
    private static List<TypeDfaState> addDfaResult(@Nonnull List<TypeDfaState> dfaResult,
                                                   @Nonnull List<TypeDfaState> oldTypes) {
      List<TypeDfaState> newTypes = new ArrayList<TypeDfaState>(oldTypes);
      for (int i = 0; i < dfaResult.size(); i++) {
        newTypes.set(i, newTypes.get(i).mergeWith(dfaResult.get(i)));
      }
      return newTypes;
    }
  }

}

