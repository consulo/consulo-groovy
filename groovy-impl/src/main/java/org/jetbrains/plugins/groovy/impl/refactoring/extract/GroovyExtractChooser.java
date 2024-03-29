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
package org.jetbrains.plugins.groovy.impl.refactoring.extract;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;
import org.jetbrains.plugins.groovy.impl.lang.psi.controlFlow.impl.GrAllVarsInitializedPolicy;
import org.jetbrains.plugins.groovy.impl.lang.psi.dataFlow.reachingDefs.FragmentVariableInfos;
import org.jetbrains.plugins.groovy.impl.lang.psi.dataFlow.reachingDefs.ReachingDefinitionsCollector;
import org.jetbrains.plugins.groovy.impl.lang.psi.dataFlow.reachingDefs.VariableInfo;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.inline.GroovyInlineMethodUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.*;

import static org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil.*;

/**
 * @author Max Medvedev
 */
public class GroovyExtractChooser {
  private static final Logger LOG = Logger.getInstance(GroovyExtractChooser.class);

  public static InitialInfo invoke(Project project, Editor editor, PsiFile file, int start, int end, boolean forceStatements) throws GrRefactoringError {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (!(file instanceof GroovyFileBase)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
      throw new GrRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
    }

    SelectionModel selectionModel = editor.getSelectionModel();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final StringPartInfo stringPart =
      StringPartInfo.findStringPart(file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());

    if (stringPart != null) {
      return new InitialInfo(new VariableInfo[0], new VariableInfo[0], PsiElement.EMPTY_ARRAY, GrStatement.EMPTY_ARRAY, new ArrayList<GrStatement>(), stringPart, project);
    }


    PsiElement[] elements = getElementsInOffset(file, start, end, forceStatements);
    if (elements.length == 1 && elements[0] instanceof GrExpression) {
      selectionModel.setSelection(start, elements[0].getTextRange().getEndOffset());
    }

    GrStatement[] statements = getStatementsByElements(elements);

    if (statements.length == 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.should.represent.a.statement.set"));
    }

    for (GrStatement statement : statements) {
      if (isSuperOrThisCall(statement, true, true)) {
        throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.contains.invocation.of.another.class.constructor"));
      }
    }

    GrStatement statement0 = statements[0];
    PsiClass owner = PsiUtil.getContextClass(statement0);
    GrStatementOwner declarationOwner = getDeclarationOwner(statement0);
    if (owner == null || declarationOwner == null && !ExtractUtil.isSingleExpression(statements)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context"));
    }
    if (declarationOwner == null &&
        ExtractUtil.isSingleExpression(statements) &&
        statement0 instanceof GrExpression &&
        PsiType.VOID.equals(((GrExpression)statement0).getType())) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.expression.has.void.type"));
    }

    if (ExtractUtil.isSingleExpression(statements) && GrIntroduceHandlerBase.expressionIsIncorrect((GrExpression)statement0, true)) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.block.should.represent.an.expression"));
    }

    if (ExtractUtil.isSingleExpression(statements) &&
        statement0.getParent() instanceof GrAssignmentExpression &&
        ((GrAssignmentExpression)statement0.getParent()).getLValue() == statement0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("selected.expression.should.not.be.lvalue"));
    }

    // collect information about return statements in selected statement set

    Set<GrStatement> allReturnStatements = new HashSet<GrStatement>();
    GrControlFlowOwner controlFlowOwner = ControlFlowUtils.findControlFlowOwner(statement0);
    LOG.assertTrue(controlFlowOwner != null);
    final Instruction[] flow = new ControlFlowBuilder(project, GrAllVarsInitializedPolicy.getInstance()).buildControlFlow(controlFlowOwner);
    allReturnStatements.addAll(ControlFlowUtils.collectReturns(flow, true));

    ArrayList<GrStatement> returnStatements = new ArrayList<GrStatement>();
    for (GrStatement returnStatement : allReturnStatements) {
      for (GrStatement statement : statements) {
        if (PsiTreeUtil.isAncestor(statement, returnStatement, false)) {
          returnStatements.add(returnStatement);
          break;
        }
      }
    }

    // collect information about variables in selected block
    FragmentVariableInfos fragmentVariableInfos =
      ReachingDefinitionsCollector.obtainVariableFlowInformation(statement0, statements[statements.length - 1], controlFlowOwner, flow);
    VariableInfo[] inputInfos = fragmentVariableInfos.getInputVariableNames();
    VariableInfo[] outputInfos = fragmentVariableInfos.getOutputVariableNames();
    if (outputInfos.length == 1 && returnStatements.size() > 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("multiple.output.values"));
    }

    boolean hasInterruptingStatements = false;

    for (GrStatement statement : statements) {
      hasInterruptingStatements = hasWrongBreakStatements(statement) || hasWrongContinueStatements(statement);
      if (hasInterruptingStatements) break;
    }

    // must be replaced by return statement
    boolean hasReturns = returnStatements.size() > 0;
    List<GrStatement> returnStatementsCopy = new ArrayList<GrStatement>(returnStatements.size());
    returnStatementsCopy.addAll(returnStatements);
    boolean isReturnStatement = isReturnStatement(statements[statements.length - 1], returnStatementsCopy);
    boolean isLastStatementOfMethod = isLastStatementOfMethodOrClosure(statements);
    if (hasReturns && !isLastStatementOfMethod && !isReturnStatement || hasInterruptingStatements) {
      throw new GrRefactoringError(
        GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow"));
    }

    return new InitialInfo(inputInfos, outputInfos, elements, statements, returnStatements, null, project);
  }

  private static boolean isLastStatementOfMethodOrClosure(GrStatement[] statements) {
    final GrStatement statement0 = statements[0];

    PsiElement returnFrom = PsiTreeUtil.getParentOfType(statement0, GrMethod.class, GrClosableBlock.class, GroovyFile.class);
    if (returnFrom instanceof GrMethod) {
      returnFrom = ((GrMethod)returnFrom).getBlock();
    }
    LOG.assertTrue(returnFrom instanceof GrStatementOwner);

    final GrStatement[] blockStatements = ((GrStatementOwner)returnFrom).getStatements();
    final GrStatement lastFromBlock = ArrayUtil.getLastElement(blockStatements);
    final GrStatement lastStatement = ArrayUtil.getLastElement(statements);
    return statement0.getManager().areElementsEquivalent(lastFromBlock, lastStatement);
  }

  private static GrStatement[] getStatementsByElements(PsiElement[] elements) {
    ArrayList<GrStatement> statementList = new ArrayList<GrStatement>();
    for (PsiElement element : elements) {
      if (element instanceof GrStatement) {
        statementList.add(((GrStatement)element));
      }
    }
    return statementList.toArray(new GrStatement[statementList.size()]);
  }

  private static PsiElement[] getElementsInOffset(PsiFile file, int startOffset, int endOffset, boolean forceStatements) {
    GrExpression expr = findElementInRange(file, startOffset, endOffset, GrExpression.class);
    if (!forceStatements && expr != null) return new PsiElement[]{expr};

    if (expr == null) {
      return findStatementsInRange(file, startOffset, endOffset, true);
    }

    if (expr.getParent() instanceof GrMethodCallExpression) {
      expr = ((GrExpression)expr.getParent());
    }
    return new PsiElement[]{expr};
  }

  private static boolean isReturnStatement(GrStatement statement, Collection<GrStatement> returnStatements) {
    if (statement instanceof GrReturnStatement) return true;
    if (statement instanceof GrIfStatement) {
      boolean checked = GroovyInlineMethodUtil.checkTailIfStatement(((GrIfStatement)statement), returnStatements);
      return checked & returnStatements.size() == 0;
    }
    if (statement instanceof GrExpression) {
      return returnStatements.contains(statement);
    }
    return false;
  }
}
