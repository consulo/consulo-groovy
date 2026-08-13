/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.AfterCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.IfEndInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.MaybeReturnInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ThrowingInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

import static consulo.util.collection.ContainerUtil.addIfNotNull;

@SuppressWarnings({"OverlyComplexClass"})
public class ControlFlowUtils {
  private static final Logger LOG = Logger.getInstance(ControlFlowUtils.class);

  public static boolean statementMayCompleteNormally(@Nullable GrStatement statement) {
    if (statement == null) {
      return true;
    }
    if (statement instanceof GrBreakStatement ||
        statement instanceof GrContinueStatement ||
        statement instanceof GrReturnStatement ||
        statement instanceof GrThrowStatement) {
      return false;
    }
    else if (statement instanceof GrForStatement forStmt) {
      return forStatementMayReturnNormally(forStmt);
    }
    else if (statement instanceof GrWhileStatement whileStmt) {
      return whileStatementMayReturnNormally(whileStmt);
    }
    else if (statement instanceof GrBlockStatement blockStmt) {
      return blockMayCompleteNormally(blockStmt);
    }
    else if (statement instanceof GrSynchronizedStatement syncStmt) {
      return openBlockMayCompleteNormally(syncStmt.getBody());
    }
    else if (statement instanceof GrLabeledStatement labeledStmt) {
      return labeledStatementMayCompleteNormally(labeledStmt);
    }
    else if (statement instanceof GrIfStatement ifStmt) {
      return ifStatementMayReturnNormally(ifStmt);
    }
    else if (statement instanceof GrTryCatchStatement tryCatchStmt) {
      return tryStatementMayReturnNormally(tryCatchStmt);
    }
    else if (statement instanceof GrSwitchStatement switchStmt) {
      return switchStatementMayReturnNormally(switchStmt);
    }
    // other statement type
    else {
      return true;
    }
  }

  private static boolean whileStatementMayReturnNormally(@Nonnull GrWhileStatement loopStatement) {
    GrCondition test = loopStatement.getCondition();
    return !BoolUtils.isTrue(test) || statementIsBreakTarget(loopStatement);
  }

  private static boolean forStatementMayReturnNormally(@Nonnull GrForStatement loopStatement) {
    return true;
  }

  private static boolean switchStatementMayReturnNormally(@Nonnull GrSwitchStatement switchStatement) {
    if (statementIsBreakTarget(switchStatement)) {
      return true;
    }
    GrCaseSection[] caseClauses = switchStatement.getCaseSections();

    if (caseClauses.length == 0) {
      return true;
    }
    boolean hasDefaultCase = false;
    for (GrCaseSection clause : caseClauses) {
      if (clause.isDefault()) {
        hasDefaultCase = true;
      }
    }

    if (!hasDefaultCase) {
      return true;
    }
    GrCaseSection lastClause = caseClauses[caseClauses.length - 1];
    GrStatement[] statements = lastClause.getStatements();
    if (statements.length == 0) {
      return true;
    }
    return statementMayCompleteNormally(statements[statements.length - 1]);
  }

  private static boolean tryStatementMayReturnNormally(@Nonnull GrTryCatchStatement tryStatement) {
    GrFinallyClause finallyBlock = tryStatement.getFinallyClause();
    if (finallyBlock != null) {
      if (!openBlockMayCompleteNormally(finallyBlock.getBody())) {
        return false;
      }
    }
    GrOpenBlock tryBlock = tryStatement.getTryBlock();
    if (openBlockMayCompleteNormally(tryBlock)) {
      return true;
    }

    for (GrCatchClause catchClause : tryStatement.getCatchClauses()) {
      if (openBlockMayCompleteNormally(catchClause.getBody())) {
        return true;
      }
    }

    return false;
  }

  private static boolean ifStatementMayReturnNormally(@Nonnull GrIfStatement ifStatement) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    if (statementMayCompleteNormally(thenBranch)) {
      return true;
    }
    GrStatement elseBranch = ifStatement.getElseBranch();
    return elseBranch == null || statementMayCompleteNormally(elseBranch);
  }

  private static boolean labeledStatementMayCompleteNormally(@Nonnull GrLabeledStatement labeledStatement) {
    GrStatement statement = labeledStatement.getStatement();
    return statementMayCompleteNormally(statement) || statementIsBreakTarget(statement);
  }

  public static boolean blockMayCompleteNormally(@Nullable GrBlockStatement block) {
    if (block == null) {
      return true;
    }
    GrStatement[] statements = block.getBlock().getStatements();
    for (GrStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  public static boolean openBlockMayCompleteNormally(@Nullable GrOpenBlock block) {
    if (block == null) {
      return true;
    }
    GrStatement[] statements = block.getStatements();
    for (GrStatement statement : statements) {
      if (!statementMayCompleteNormally(statement)) {
        return false;
      }
    }
    return true;
  }

  private static boolean statementIsBreakTarget(@Nonnull GrStatement statement) {
    BreakFinder breakFinder = new BreakFinder(statement);
    statement.accept(breakFinder);
    return breakFinder.breakFound();
  }

  public static boolean statementContainsReturn(@Nonnull GrStatement statement) {
    ReturnFinder returnFinder = new ReturnFinder();
    statement.accept(returnFinder);
    return returnFinder.returnFound();
  }

  public static boolean statementIsContinueTarget(@Nonnull GrStatement statement) {
    ContinueFinder continueFinder = new ContinueFinder(statement);
    statement.accept(continueFinder);
    return continueFinder.continueFound();
  }

  public static boolean isInLoop(@Nonnull GroovyPsiElement element) {
    return isInForStatementBody(element) ||
        isInWhileStatementBody(element);
  }

  public static boolean isInFinallyBlock(@Nonnull GroovyPsiElement element) {
    GrFinallyClause containingClause = PsiTreeUtil.getParentOfType(element, GrFinallyClause.class);
    if (containingClause == null) {
      return false;
    }
    GrOpenBlock body = containingClause.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInWhileStatementBody(@Nonnull GroovyPsiElement element) {
    GrWhileStatement whileStatement = PsiTreeUtil.getParentOfType(element, GrWhileStatement.class);
    if (whileStatement == null) {
      return false;
    }
    GrStatement body = whileStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  private static boolean isInForStatementBody(@Nonnull GroovyPsiElement element) {
    GrForStatement forStatement = PsiTreeUtil.getParentOfType(element, GrForStatement.class);
    if (forStatement == null) {
      return false;
    }
    GrStatement body = forStatement.getBody();
    return PsiTreeUtil.isAncestor(body, element, true);
  }

  public static GrStatement stripBraces(@Nonnull GrStatement branch) {
    if (branch instanceof GrBlockStatement) {
      GrBlockStatement block = (GrBlockStatement)branch;
      GrStatement[] statements = block.getBlock().getStatements();
      if (statements.length == 1) {
        return statements[0];
      }
      else {
        return block;
      }
    }
    else {
      return branch;
    }
  }

  public static boolean statementCompletesWithStatement(@Nonnull GrStatement containingStatement, @Nonnull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (statementToCheck.equals(containingStatement)) {
        return true;
      }
      GroovyPsiElement container = getContainingStatement(statementToCheck);
      if (container == null) {
        return false;
      }
      if (container instanceof GrBlockStatement blockStmt) {
        if (!statementIsLastInBlock(blockStmt, (GrStatement)statementToCheck)) {
          return false;
        }
      }
      if (isLoop(container)) {
        return false;
      }
      statementToCheck = container;
    }
  }

  public static boolean blockCompletesWithStatement(@Nonnull GrBlockStatement body, @Nonnull GrStatement statement) {
    GrStatement statementToCheck = statement;
    while (true) {
      if (statementToCheck == null) {
        return false;
      }
      GrStatement container = getContainingStatement(statementToCheck);
      if (container == null) {
        return false;
      }
      if (isLoop(container)) {
        return false;
      }
      if (container instanceof GrBlockStatement blockStmt) {
        if (!statementIsLastInBlock(blockStmt, statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
      }
      else {
        statementToCheck = container;
      }
    }
  }

  public static boolean openBlockCompletesWithStatement(@Nonnull GrCodeBlock body, @Nonnull GrStatement statement) {
    GroovyPsiElement elementToCheck = statement;
    while (true) {
      if (elementToCheck == null) return false;

      GroovyPsiElement container =
        PsiTreeUtil.getParentOfType(elementToCheck, GrStatement.class, GrCodeBlock.class, GrCaseSection.class);
      if (container == null) return false;

      if (isLoop(container)) return false;

      if (container instanceof GrCaseSection) {
        GrSwitchStatement switchStatement = (GrSwitchStatement)container.getParent();
        GrCaseSection[] sections = switchStatement.getCaseSections();
        if (container == sections[sections.length - 1]) return false;
      }

      if (container instanceof GrCodeBlock codeBlock) {
        if (elementToCheck instanceof GrStatement statementToCheck) {
          if (!statementIsLastInCodeBlock(codeBlock, statementToCheck)) {
            return false;
          }
        }
        if (container instanceof GrOpenBlock || container instanceof GrClosableBlock) {
          if (container.equals(body)) {
            return true;
          }
          elementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
        }
        else {
          elementToCheck = container;
        }
      }
      else {
        elementToCheck = container;
      }
    }
  }

  public static boolean closureCompletesWithStatement(@Nonnull GrClosableBlock body, @Nonnull GrStatement statement) {
    GroovyPsiElement statementToCheck = statement;
    while (true) {
      if (!(statementToCheck instanceof GrExpression || statementToCheck instanceof GrReturnStatement)) {
        return false;
      }
      GroovyPsiElement container = getContainingStatementOrBlock(statementToCheck);
      if (container == null) {
        return false;
      }
      if (isLoop(container)) {
        return false;
      }
      if (container instanceof GrCodeBlock codeBlock) {
        if (!statementIsLastInCodeBlock(codeBlock, (GrStatement)statementToCheck)) {
          return false;
        }
        if (container.equals(body)) {
          return true;
        }
        statementToCheck = PsiTreeUtil.getParentOfType(container, GrStatement.class);
      }
      else {
        statementToCheck = container;
      }
    }
  }

  private static boolean isLoop(@Nonnull GroovyPsiElement element) {
    return element instanceof GrLoopStatement;
  }

  @Nullable
  private static GrStatement getContainingStatement(@Nonnull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class);
  }

  @Nullable
  private static GroovyPsiElement getContainingStatementOrBlock(@Nonnull GroovyPsiElement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrStatement.class, GrCodeBlock.class);
  }

  private static boolean statementIsLastInBlock(@Nonnull GrBlockStatement block, @Nonnull GrStatement statement) {
    GrStatement[] statements = block.getBlock().getStatements();
    for (int i = statements.length - 1; i >= 0; i--) {
      GrStatement childStatement = statements[i];
      if (statement.equals(childStatement)) {
        return true;
      }
      if (!(childStatement instanceof GrReturnStatement)) {
        return false;
      }
    }
    return false;
  }

  private static boolean statementIsLastInCodeBlock(@Nonnull GrCodeBlock block, @Nonnull GrStatement statement) {
    GrStatement[] statements = block.getStatements();
    for (int i = statements.length - 1; i >= 0; i--) {
      GrStatement childStatement = statements[i];
      if (statement.equals(childStatement)) {
        return true;
      }
      if (!(childStatement instanceof GrReturnStatement)) {
        return false;
      }
    }
    return false;
  }

  public static List<GrStatement> collectReturns(@Nullable PsiElement element) {
    return collectReturns(element, element instanceof GrCodeBlock || element instanceof GroovyFile);
  }
  public static List<GrStatement> collectReturns(@Nullable PsiElement element, boolean allExitPoints) {
    if (element == null) return Collections.emptyList();

    Instruction[] flow;
    if (element instanceof GrControlFlowOwner) {
      flow = ((GrControlFlowOwner)element).getControlFlow();
    }
    else {
      flow = new ControlFlowBuilder(element.getProject()).buildControlFlow((GroovyPsiElement)element);
    }
    return collectReturns(flow, allExitPoints);
  }

  public static List<GrStatement> collectReturns(@Nonnull Instruction[] flow, boolean allExitPoints) {
    boolean[] visited = new boolean[flow.length];
    List<GrStatement> res = new ArrayList<>();
    visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, (instruction, returnValue) -> {
      PsiElement element = instruction.getElement();
      if (element instanceof GrReturnStatement || (allExitPoints && instruction instanceof MaybeReturnInstruction)) {
        res.add((GrStatement)element);
      }
      return true;
    });
    return res;
  }

  @Nullable
  public static GrExpression extractReturnExpression(GrStatement returnStatement) {
    if (returnStatement instanceof GrReturnStatement returnStmt) return returnStmt.getReturnValue();
    if (returnStatement instanceof GrExpression expr) return expr;
    return null;
  }

  public static boolean isIncOrDecOperand(GrReferenceExpression referenceExpression) {
    PsiElement parent = referenceExpression.getParent();
    if (parent instanceof GrUnaryExpression unaryExpression) {
      IElementType opType = unaryExpression.getOperationTokenType();
      return opType == GroovyTokenTypes.mDEC || opType == GroovyTokenTypes.mINC;
    }

    return false;
  }

  public static String dumpControlFlow(Instruction[] instructions) {
    StringBuilder builder = new StringBuilder();
    for (Instruction instruction : instructions) {
      builder.append(instruction.toString()).append("\n");
    }

    return builder.toString();
  }

  @Nullable
  public static ReadWriteVariableInstruction findRWInstruction(GrReferenceExpression refExpr, Instruction[] flow) {
    for (Instruction instruction : flow) {
      if (instruction instanceof ReadWriteVariableInstruction rwVarInsn && rwVarInsn.getElement() == refExpr) {
        return rwVarInsn;
      }
    }
    return null;
  }

  @Nullable
  public static Instruction findNearestInstruction(PsiElement place, Instruction[] flow) {
    List<Instruction> applicable = new ArrayList<>();
    for (Instruction instruction : flow) {
      PsiElement element = instruction.getElement();
      if (element == null) continue;

      if (element == place) return instruction;

      if (PsiTreeUtil.isAncestor(element, place, true)) {
        applicable.add(instruction);
      }
    }
    if (applicable.size() == 0) return null;

    Collections.sort(applicable, new Comparator<>() {
      @Override
      @RequiredReadAction
      public int compare(Instruction o1, Instruction o2) {
        TextRange t1 = Objects.requireNonNull(o1.getElement()).getTextRange();
        TextRange t2 = Objects.requireNonNull(o2.getElement()).getTextRange();
        int s1 = t1.getStartOffset();
        int s2 = t2.getStartOffset();

        if (s1 == s2) {
          return t1.getEndOffset() - t2.getEndOffset();
        }
        return s2 - s1;
      }
    });

    return applicable.get(0);
  }

  private static class ReturnFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;

    public boolean returnFound() {
      return m_found;
    }

    @Override
    @RequiredReadAction
    public void visitReturnStatement(@Nonnull GrReturnStatement returnStatement) {
      if (m_found) {
        return;
      }
      super.visitReturnStatement(returnStatement);
      m_found = true;
    }
  }

  private static class BreakFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;
    private final GrStatement m_target;

    private BreakFinder(@Nonnull GrStatement target) {
      super();
      m_target = target;
    }

    public boolean breakFound() {
      return m_found;
    }

    @Override
    @RequiredReadAction
    public void visitBreakStatement(@Nonnull GrBreakStatement breakStatement) {
      if (m_found) {
        return;
      }
      super.visitBreakStatement(breakStatement);
      GrStatement exitedStatement = breakStatement.findTargetStatement();
      if (exitedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
        m_found = true;
      }
    }
  }

  private static class ContinueFinder extends GroovyRecursiveElementVisitor {
    private boolean m_found = false;
    private final GrStatement m_target;

    private ContinueFinder(@Nonnull GrStatement target) {
      super();
      m_target = target;
    }

    public boolean continueFound() {
      return m_found;
    }

    @Override
    @RequiredReadAction
    public void visitContinueStatement(@Nonnull GrContinueStatement continueStatement) {
      if (m_found) {
        return;
      }
      super.visitContinueStatement(continueStatement);
      GrStatement exitedStatement =
          continueStatement.findTargetStatement();
      if (exitedStatement == null) {
        return;
      }
      if (PsiTreeUtil.isAncestor(exitedStatement, m_target, false)) {
        m_found = true;
      }
    }
  }

  public interface ExitPointVisitor {
    boolean visitExitPoint(Instruction instruction, @Nullable GrExpression returnValue);
  }

  public static Set<GrExpression> getAllReturnValues(@Nonnull GrControlFlowOwner block) {
    return CachedValuesManager.getManager(block.getProject()).getCachedValue(block, () -> {
      Set<GrExpression> result = new HashSet<>();
      visitAllExitPoints(block, (instruction, returnValue) -> {
        addIfNotNull(result, returnValue);
        return true;
      });
      return CachedValueProvider.Result.create(result, block);
    });
  }

  public static boolean isReturnValue(@Nonnull GrExpression expression, @Nonnull GrControlFlowOwner flowOwner) {
    return getAllReturnValues(flowOwner).contains(expression);
  }

  public static boolean visitAllExitPoints(@Nullable GrControlFlowOwner block, ExitPointVisitor visitor) {
    if (block == null) return true;
    Instruction[] flow = block.getControlFlow();
    boolean[] visited = new boolean[flow.length];
    return visitAllExitPointsInner(flow[flow.length - 1], flow[0], visited, visitor);
  }

  private static boolean visitAllExitPointsInner(Instruction last, Instruction first, boolean[] visited, ExitPointVisitor visitor) {
    if (first == last) return true;
    if (last instanceof AfterCallInstruction) {
      visited[last.num()] = true;
      return visitAllExitPointsInner(((AfterCallInstruction)last).myCall, first, visited, visitor);
    }
    
    if (last instanceof MaybeReturnInstruction) {
      return visitor.visitExitPoint(last, (GrExpression)last.getElement());
    }
    else if (last instanceof IfEndInstruction) {
      visited[last.num()] = true;
      for (Instruction instruction : last.allPredecessors()) {
        if (!visitAllExitPointsInner(instruction, first, visited, visitor)) return false;
      }
      return true;
    }
    else if (last instanceof ThrowingInstruction) {
      PsiElement element = last.getElement();
      if (!(element instanceof GrThrowStatement || element instanceof GrAssertStatement)) return true;
    }

    PsiElement element = last.getElement();
    if (element != null) {
      GrExpression returnValue;
      if (element instanceof GrReturnStatement returnStmt) {
        returnValue = returnStmt.getReturnValue();
      }
      else if (element instanceof GrExpression expr && PsiUtil.isExpressionStatement(expr)) {
        returnValue = expr;
      }
      else {
        returnValue = null;
      }

      return visitor.visitExitPoint(last, returnValue);
    }
    visited[last.num()] = true;
    for (Instruction pred : last.allPredecessors()) {
      if (!visited[pred.num()]) {
        if (!visitAllExitPointsInner(pred, first, visited, visitor)) return false;
      }
    }
    return true;
  }

  @Nullable
  @RequiredReadAction
  public static GrControlFlowOwner findControlFlowOwner(PsiElement place) {
    if (place instanceof GrCodeBlock) {
      place = place.getContext();
    }
    while (true) {
      assert place != null;
      place = place.getContext();
      if (place == null) return null;
      if (place instanceof GrControlFlowOwner controlFlowOwner && controlFlowOwner.isTopControlFlowOwner()) return controlFlowOwner;
      if (place instanceof GrMethod method) return method.getBlock();
      if (place instanceof GrClassInitializer classInitializer) return classInitializer.getBlock();
    }
  }

  /**
   * searches for next or previous write access to local variable
   * @param local variable to analyze
   * @param place place to start searching
   * @param ahead if true search for next write. if false searches for previous write
   * @return all write instructions leading to (or preceding) the place
   */
  @RequiredReadAction
  public static ReadWriteVariableInstruction[] findWriteAccess(GrVariable local, PsiElement place, boolean ahead) {
    List<ReadWriteVariableInstruction> res = findAccess(local, place, ahead, true);
    return res.toArray(new ReadWriteVariableInstruction[res.size()]);
  }

  @RequiredReadAction
  public static List<ReadWriteVariableInstruction> findAccess(GrVariable local, PsiElement place, boolean ahead, boolean writeAccessOnly) {
    LOG.assertTrue(!(local instanceof GrField), local.getClass());

    GrControlFlowOwner owner = findControlFlowOwner(local);
    assert owner != null;

    Instruction cur = findInstruction(place, owner.getControlFlow());

    if (cur == null) {
      throw new IllegalArgumentException("place is not in the flow");
    }

    return findAccess(local, ahead, writeAccessOnly, cur);
  }

  public static List<ReadWriteVariableInstruction> findAccess(GrVariable local, boolean ahead, boolean writeAccessOnly, Instruction cur) {
    String name = local.getName();

    List<ReadWriteVariableInstruction> result = new ArrayList<>();
    Set<Instruction> visited = new HashSet<>();

    visited.add(cur);

    Queue<Instruction> queue = new ArrayDeque<>();

    for (Instruction i : ahead ? cur.allSuccessors() : cur.allPredecessors()) {
      if (visited.add(i)) {
        queue.add(i);
      }
    }

    while (true) {
      Instruction instruction = queue.poll();
      if (instruction == null) break;

      if (instruction instanceof ReadWriteVariableInstruction) {
        ReadWriteVariableInstruction rw = (ReadWriteVariableInstruction)instruction;
        if (name.equals(rw.getVariableName())) {
          if (rw.isWrite()) {
            result.add(rw);
            continue;
          }

          if (!writeAccessOnly) {
            result.add(rw);
          }
        }
      }

      for (Instruction i : ahead ? instruction.allSuccessors() : instruction.allPredecessors()) {
        if (visited.add(i)) {
          queue.add(i);
        }
      }
    }

    return result;
  }

  @Nullable
  public static Instruction findInstruction(PsiElement place, Instruction[] controlFlow) {
    return ContainerUtil.find(controlFlow, instruction -> instruction.getElement() == place);
  }

  public static List<Instruction> findAllInstructions(PsiElement place, Instruction[] controlFlow) {
    return ContainerUtil.findAll(controlFlow, instruction -> instruction.getElement() == place);
  }

  @Nonnull
  public static ArrayList<BitSet> inferWriteAccessMap(final Instruction[] flow, final GrVariable var) {
    Semilattice<BitSet> sem = new Semilattice<>() {
      @Override
      public BitSet join(ArrayList<BitSet> ins) {
        BitSet result = new BitSet(flow.length);
        for (BitSet set : ins) {
          result.or(set);
        }
        return result;
      }

      @Override
      public boolean eq(BitSet e1, BitSet e2) {
        return e1.equals(e2);
      }
    };

    DfaInstance<BitSet> dfa = new DfaInstance<>() {
      @Override
      @RequiredReadAction
      public void fun(BitSet bitSet, Instruction instruction) {
        if (!(instruction instanceof ReadWriteVariableInstruction rwVarInsn)) return;
          if (!rwVarInsn.isWrite()) return;

        PsiElement element = rwVarInsn.getElement();
        if (element instanceof GrVariable && element != var) return;
        if (element instanceof GrReferenceExpression ref) {
          if (ref.isQualified() || ref.resolve() != var) {
            return;
          }
        }
          if (!rwVarInsn.getVariableName().equals(var.getName())) {
          return;
        }

        bitSet.clear();
        bitSet.set(rwVarInsn.num());
      }

      @Nonnull
      @Override
      public BitSet initial() {
        return new BitSet(flow.length);
      }

      @Override
      public boolean isForward() {
        return true;
      }
    };

    return new DFAEngine<>(flow, dfa, sem).performForceDFA();
  }
}
