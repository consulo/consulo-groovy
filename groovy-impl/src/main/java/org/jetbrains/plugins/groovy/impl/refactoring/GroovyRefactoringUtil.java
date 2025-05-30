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

package org.jetbrains.plugins.groovy.impl.refactoring;

import com.intellij.java.language.psi.PsiElementFactory;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.MethodSignature;
import com.intellij.java.language.psi.util.PsiUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.PsiEquivalenceUtil;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.reflect.ReflectionUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

import java.util.*;

import static com.intellij.java.impl.refactoring.util.RefactoringUtil.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isNewLine;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses;

/**
 * @author ilyas
 */
public abstract class GroovyRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(GroovyRefactoringUtil.class);

  public static final Collection<String> KEYWORDS = ContainerUtil.map(
      TokenSets.KEYWORDS.getTypes(), IElementType::toString);

  private static final String[] finalModifiers = new String[]{PsiModifier.FINAL};

  @Nullable
  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place.getParent();
    while (true) {
      if (parent == null) {
        return null;
      }
      if (parent instanceof GrDeclarationHolder && !(parent instanceof GrClosableBlock && parent.getParent() instanceof GrStringInjection)) {
        return parent;
      }
      if (parent instanceof GrLoopStatement) {
        return parent;
      }

      parent = parent.getParent();
    }
  }

  @Nullable
  public static <T extends PsiElement> T findElementInRange(final PsiFile file,
                                                            int startOffset,
                                                            int endOffset,
                                                            final Class<T> klass) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, file.getLanguage());
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, file.getLanguage());
    if (element1 == null || element2 == null) return null;

    if (TokenSets.WHITE_SPACES_SET.contains(element1.getNode().getElementType())) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, file.getLanguage());
    }
    if (TokenSets.WHITE_SPACES_SET.contains(element2.getNode().getElementType())) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, file.getLanguage());
    }

    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    assert commonParent != null;
    final T element = ReflectionUtil.isAssignable(klass, commonParent.getClass()) ? (T) commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);
    if (element == null || element.getTextRange().getStartOffset() != startOffset) {
      return null;
    }
    return element;
  }

  public static PsiElement[] getExpressionOccurrences(@Nonnull PsiElement expr, @Nonnull PsiElement scope) {
    ArrayList<PsiElement> occurrences = new ArrayList<>();
    Comparator<PsiElement> comparator = new Comparator<>() {
      public int compare(PsiElement element1, PsiElement element2) {
        if (element1 != null && element1.equals(element2)) return 0;

        if (element1 instanceof GrParameter &&
            element2 instanceof GrParameter) {
          final String name1 = ((GrParameter) element1).getName();
          final String name2 = ((GrParameter) element2).getName();
          return name1.compareTo(name2);
        }
        return 1;
      }
    };

    if (scope instanceof GrLoopStatement) {
      PsiElement son = expr;
      while (son.getParent() != null && !(son.getParent() instanceof GrLoopStatement)) {
        son = son.getParent();
      }
      assert scope.equals(son.getParent());
      collectOccurrences(expr, son, occurrences, comparator, false);
    } else {
      collectOccurrences(expr, scope, occurrences, comparator, scope instanceof GrTypeDefinition || scope instanceof GroovyFileBase);
    }
    return PsiUtilCore.toPsiElementArray(occurrences);
  }


  private static void collectOccurrences(@Nonnull PsiElement expr, @Nonnull PsiElement scope, @Nonnull ArrayList<PsiElement> acc, Comparator<PsiElement> comparator, boolean goIntoInner) {
    if (scope.equals(expr)) {
      acc.add(expr);
      return;
    }
    for (PsiElement child : scope.getChildren()) {
      if (goIntoInner || !(child instanceof GrTypeDefinition) && !(child instanceof GrMethod && scope instanceof GroovyFileBase)) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, comparator, false)) {
          acc.add(child);
        } else {
          collectOccurrences(expr, child, acc, comparator, goIntoInner);
        }
      }
    }
  }


  public static boolean isAppropriateContainerForIntroduceVariable(PsiElement tempContainer) {
    return tempContainer instanceof GrOpenBlock ||
        tempContainer instanceof GrClosableBlock ||
        tempContainer instanceof GroovyFileBase ||
        tempContainer instanceof GrCaseSection ||
        tempContainer instanceof GrLoopStatement ||
        tempContainer instanceof GrIfStatement;
  }

  public static void sortOccurrences(PsiElement[] occurrences) {
    Arrays.sort(occurrences, new Comparator<>() {
      public int compare(PsiElement elem1, PsiElement elem2) {
        final int offset1 = elem1.getTextRange().getStartOffset();
        final int offset2 = elem2.getTextRange().getStartOffset();
        return offset1 - offset2;
      }
    });
  }

  public static boolean isLocalVariable(@Nullable PsiElement variable) {
    return variable instanceof GrVariable && !(variable instanceof GrField || variable instanceof GrParameter);
  }


  public static void highlightOccurrences(Project project, @Nullable Editor editor, PsiElement[] elements) {
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    if (elements.length > 0) {
      highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
    }
  }

  public static void highlightOccurrencesByRanges(Project project, Editor editor, TextRange[] ranges) {
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    for (TextRange range : ranges) {
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
    }
  }

  public static void trimSpacesAndComments(Editor editor, PsiFile file, boolean trimComments) {
    int start = editor.getSelectionModel().getSelectionStart();
    int end = editor.getSelectionModel().getSelectionEnd();
    while (file.findElementAt(start) instanceof PsiWhiteSpace ||
        (file.findElementAt(start) instanceof PsiComment && trimComments) ||
        (file.findElementAt(start) != null &&
            GroovyTokenTypes.mNLS.equals(file.findElementAt(start).getNode().getElementType()))) {
      start++;
    }
    while (file.findElementAt(end - 1) instanceof PsiWhiteSpace ||
        (file.findElementAt(end - 1) instanceof PsiComment && trimComments) ||
        (file.findElementAt(end - 1) != null &&
            (GroovyTokenTypes.mNLS.equals(file.findElementAt(end - 1).getNode().getElementType()) ||
                GroovyTokenTypes.mSEMI.equals(file.findElementAt(end - 1).getNode().getElementType())))) {
      end--;
    }

    editor.getSelectionModel().setSelection(start, end);
  }

  @Nonnull
  public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset, boolean strict) {
    if (!(file instanceof GroovyFileBase)) return PsiElement.EMPTY_ARRAY;
    Language language = GroovyFileType.GROOVY_LANGUAGE;
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);

    if (element1 instanceof PsiWhiteSpace || isNewLine(element1)) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace || isNewLine(element2)) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof GrCodeBlock) break;
      if (parent instanceof GroovyFileBase) break;
      if (parent instanceof GrCaseSection) break;
      if (parent instanceof GrStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent == null) return PsiElement.EMPTY_ARRAY;
      final PsiElement prev = parent;
      parent = parent.getParent();
      if (parent instanceof GrCodeBlock && prev instanceof LeafPsiElement) { //braces
        parent = parent.getParent();
      }
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset() && strict) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset() && strict) return PsiElement.EMPTY_ARRAY;

    if (parent instanceof GrCodeBlock && parent.getParent() instanceof GrBlockStatement &&
        element1 == ((GrCodeBlock) parent).getLBrace() && element2 == ((GrCodeBlock) parent).getRBrace()) {
      return new PsiElement[]{parent.getParent()};
    }

    // calculate children
    PsiElement[] children = PsiElement.EMPTY_ARRAY;
    PsiElement psiChild = parent.getFirstChild();
    if (psiChild != null) {
      List<PsiElement> result = new ArrayList<>();
      while (psiChild != null) {
        result.add(psiChild);
        psiChild = psiChild.getNextSibling();
      }
      children = PsiUtilCore.toPsiElementArray(result);
    }


    ArrayList<PsiElement> possibleStatements = new ArrayList<>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child == element1) {
        flag = true;
      }
      if (flag) {
        possibleStatements.add(child);
      }
      if (child == element2) {
        break;
      }
    }

    for (PsiElement element : possibleStatements) {
      if (!(element instanceof GrStatement ||
          element instanceof PsiWhiteSpace ||
          element instanceof PsiComment ||
          TokenSets.SEPARATORS.contains(element.getNode().getElementType()))) {
        return PsiElement.EMPTY_ARRAY;
      }
    }

    return PsiUtilCore.toPsiElementArray(possibleStatements);
  }

  public static boolean isSuperOrThisCall(GrStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof GrConstructorInvocation)) return false;
    GrConstructorInvocation expr = (GrConstructorInvocation) statement;
    return (testForSuper && expr.isSuperCall()) || (testForThis && expr.isThisCall());
  }

  public static boolean hasWrongBreakStatements(PsiElement element) {
    ArrayList<GrBreakStatement> vector = new ArrayList<>();
    addBreakStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addBreakStatements(PsiElement element, ArrayList<GrBreakStatement> vector) {
    if (element instanceof GrBreakStatement) {
      vector.add(((GrBreakStatement) element));
    } else if (!(element instanceof GrLoopStatement ||
        element instanceof GrSwitchStatement ||
        element instanceof GrClosableBlock)) {
      for (PsiElement psiElement : element.getChildren()) {
        addBreakStatements(psiElement, vector);
      }
    }
  }

  public static boolean hasWrongContinueStatements(PsiElement element) {
    ArrayList<GrContinueStatement> vector = new ArrayList<>();
    addContinueStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addContinueStatements(PsiElement element, ArrayList<GrContinueStatement> vector) {
    if (element instanceof GrContinueStatement) {
      vector.add(((GrContinueStatement) element));
    } else if (!(element instanceof GrLoopStatement || element instanceof GrClosableBlock)) {
      for (PsiElement psiElement : element.getChildren()) {
        addContinueStatements(psiElement, vector);
      }
    }
  }


  public static String getMethodSignature(PsiMethod method) {
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    String s = signature.getName() + "(";
    int i = 0;
    PsiType[] types = signature.getParameterTypes();
    for (PsiType type : types) {
      s += type.getPresentableText();
      if (i < types.length - 1) {
        s += ", ";
      }
      i++;
    }
    s += ")";
    return s;

  }

  @Nullable
  public static GrCall getCallExpressionByMethodReference(@Nullable PsiElement ref) {
    if (ref == null) return null;
    if (ref instanceof GrEnumConstant) return (GrEnumConstant)ref;
    if (ref instanceof GrConstructorInvocation) return (GrCall)ref;
    PsiElement parent = ref.getParent();
    if (parent instanceof GrCall) {
      return (GrCall)parent;
    }
    else {
      return null;
    }
  }

  public static boolean isMethodUsage(PsiElement ref) {
    return (ref instanceof GrEnumConstant) || (ref.getParent() instanceof GrCall) || (ref instanceof GrConstructorInvocation);
  }

  public static String createTempVar(GrExpression expr, final GroovyPsiElement context, boolean declareFinal) {
    expr = addBlockIntoParent(expr);
    final GrVariableDeclarationOwner block = PsiTreeUtil.getParentOfType(expr, GrVariableDeclarationOwner.class);
    LOG.assertTrue(block != null);
    final PsiElement anchorStatement = PsiTreeUtil.findPrevParent(block, expr);
    LOG.assertTrue(anchorStatement instanceof GrStatement);

    Project project = expr.getProject();
    String[] suggestedNames =GroovyNameSuggestionUtil.suggestVariableNames(expr, new NameValidator() {
      public String validateName(String name, boolean increaseNumber) {
        return name;
      }

      public Project getProject() {
        return context.getProject();
      }
    });
/*
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expr, null).names;*/
    final String prefix = suggestedNames[0];
    final String id = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(prefix, context, true);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expr.getProject());
    String[] modifiers;
    if (declareFinal) {
      modifiers = finalModifiers;
    }
    else {
      modifiers = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    GrVariableDeclaration decl =
      factory.createVariableDeclaration(modifiers, (GrExpression)skipParentheses(expr, false), expr.getType(), id);
/*    if (declareFinal) {
      com.intellij.java.language.psi.util.PsiUtil.setModifierProperty((decl.getMembers()[0]), PsiModifier.FINAL, true);
    }*/
    final GrStatement statement = ((GrStatementOwner)anchorStatement.getParent()).addStatementBefore(decl, (GrStatement)anchorStatement);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(statement);

    return id;
  }

  public static int verifySafeCopyExpression(GrExpression expression) {
    return verifySafeCopyExpressionSubElement(expression);
  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof GrNamedElement) {
      return EXPR_COPY_SAFE;
    }

    if (element instanceof GrMethodCallExpression) {
      result = EXPR_COPY_UNSAFE;
    }

    if (element instanceof GrNewExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrAssignmentExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrClosableBlock) {
      return EXPR_COPY_PROHIBITED;
    }

    if (isPlusPlusOrMinusMinus(element)) {
      return EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
  }

  public static boolean isPlusPlusOrMinusMinus(PsiElement element) {
    if (element instanceof GrUnaryExpression) {
      IElementType operandSign = ((GrUnaryExpression)element).getOperationTokenType();
      return operandSign == GroovyTokenTypes.mDEC || operandSign == GroovyTokenTypes.mINC;
    }
    return false;
  }

  public static boolean isCorrectReferenceName(String newName, Project project) {
    if (newName.startsWith("'''") || newName.startsWith("\"\"\"")) {
      if (newName.length() < 6 || !newName.endsWith("'''")) {
        return false;
      }
    }
    else if (StringUtil.startsWithChar(newName, '\'') || StringUtil.startsWithChar(newName, '"')) {
      if (newName.length() < 2 || !newName.endsWith("'")) {
        return false;
      }
    }
    if (KEYWORDS.contains(newName)) {
      return false;
    }
    try {
      GroovyPsiElementFactory.getInstance(project).createReferenceNameFromText(newName);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return true;
  }

  public static GrExpression generateArgFromMultiArg(PsiSubstitutor substitutor,
                                                     List<? extends PsiElement> arguments,
                                                     @Nullable PsiType type,
                                                     final Project project) {
    StringBuilder argText = new StringBuilder();
    argText.append("[");
    for (PsiElement argument : arguments) {
      argText.append(argument.getText()).append(", ");
    }
    if (arguments.size() > 0) {
      argText.delete(argText.length() - 2, argText.length());
    }
    argText.append("]");
    if (type instanceof PsiArrayType) {
      type = substitutor.substitute(type);
      String typeText = type.getCanonicalText();
      if (type instanceof PsiEllipsisType) {
        typeText = typeText.replace("...", "[]");
      }
      argText.append(" as ").append(typeText);
    }
    return GroovyPsiElementFactory.getInstance(project).createExpressionFromText(argText.toString());
  }

  public static boolean hasSideEffect(@Nonnull GroovyPsiElement statement) {
    final Ref<Boolean> hasSideEffect = new Ref<>(false);
    statement.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitCallExpression(GrCallExpression callExpression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitUnaryExpression(GrUnaryExpression expression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitElement(GroovyPsiElement element) {
        if (hasSideEffect.get()) return;
        super.visitElement(element);
      }
    });

    return hasSideEffect.get();
  }

  /**
   *  adds block statement in parent of statement if needed. For Example:
   *    while (true) a=foo()
   *  will be replaced with
   *    while(true) {a=foo()}
   * @param statement
   * @return corresponding statement inside block if it has been created or statement itself.
   * @throws IncorrectOperationException
   */

  @Nonnull
  public static <Type extends PsiElement> Type addBlockIntoParent(@Nonnull Type statement) throws IncorrectOperationException {

    PsiElement parent = statement.getParent();
    PsiElement child = statement;
    while (!(parent instanceof GrLoopStatement) &&
           !(parent instanceof GrIfStatement) &&
           !(parent instanceof GrVariableDeclarationOwner) &&
           parent != null) {
      parent = parent.getParent();
      child = child.getParent();
    }
    if (parent instanceof GrWhileStatement && child == ((GrWhileStatement)parent).getCondition() ||
        parent instanceof GrIfStatement && child == ((GrIfStatement)parent).getCondition()) {
      parent = parent.getParent();
    }
    assert parent != null;
    if (parent instanceof GrVariableDeclarationOwner) {
      return statement;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
    PsiElement tempStmt = statement;
    while (parent != tempStmt.getParent()) {
      tempStmt = tempStmt.getParent();
    }
    GrStatement toAdd = (GrStatement)tempStmt.copy();
    GrBlockStatement blockStatement = factory.createBlockStatement();
    if (parent instanceof GrLoopStatement) {
      ((GrLoopStatement)parent).replaceBody(blockStatement);
    }
    else {
      GrIfStatement ifStatement = (GrIfStatement)parent;
      if (tempStmt == ifStatement.getThenBranch()) {
        ifStatement.replaceThenBranch(blockStatement);
      }
      else if (tempStmt == ifStatement.getElseBranch()) {
        ifStatement.replaceElseBranch(blockStatement);
      }
    }
    GrStatement result = blockStatement.getBlock().addStatementBefore(toAdd, null);
    if (result instanceof GrReturnStatement) {
      //noinspection ConstantConditions,unchecked
      statement = (Type)((GrReturnStatement)result).getReturnValue();
    }
    else {
      //noinspection unchecked
      statement = (Type)result;
    }

    return statement;
  }

  public static boolean isDiamondNewOperator(GrExpression expression) {
    PsiElement element = skipParentheses(expression, false);
    if (!(element instanceof GrNewExpression)) return false;
    if (((GrNewExpression)element).getArrayCount() > 0) return false;

    GrCodeReferenceElement referenceElement = ((GrNewExpression)element).getReferenceElement();
    if (referenceElement == null) return false;

    GrTypeArgumentList typeArgumentList = referenceElement.getTypeArgumentList();
    return typeArgumentList != null && typeArgumentList.isDiamond();
  }

  public static boolean checkPsiElementsAreEqual(PsiElement l, PsiElement r) {
    if (!l.getText().equals(r.getText())) return false;
    if (l.getNode().getElementType() != r.getNode().getElementType()) return false;

    final PsiElement[] lChildren = l.getChildren();
    final PsiElement[] rChildren = r.getChildren();

    if (lChildren.length != rChildren.length) return false;

    for (int i = 0; i < rChildren.length; i++) {
      if (!checkPsiElementsAreEqual(lChildren[i], rChildren[i])) return false;
    }
    return true;
  }

  @Nullable
  public static GrStatementOwner getDeclarationOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    return parent instanceof GrStatementOwner ? ((GrStatementOwner) parent) : null;
  }

  @Nullable
  public static PsiType getType(@Nullable PsiParameter myParameter) {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
  }

    @Nullable
  public static PsiType getSubstitutedType(@Nullable GrParameter parameter) {
    if (parameter == null) return null;

    final PsiType type = getType(parameter);

    if (type instanceof PsiArrayType) {
      return type;
    }

    final PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = result.getElement();
    if (psiClass == null) return type;
    final HashSet<PsiTypeParameter> usedTypeParameters = new HashSet<>();
    collectTypeParameters(usedTypeParameters, parameter);
    for (Iterator<PsiTypeParameter> iterator = usedTypeParameters.iterator(); iterator.hasNext(); ) {
      PsiTypeParameter usedTypeParameter = iterator.next();
      if (parameter.getDeclarationScope() != usedTypeParameter.getOwner()) {
        iterator.remove();
      }
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parameter.getProject());
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      subst = subst.put(usedTypeParameter, TypeConversionUtil.typeParameterErasure(usedTypeParameter));
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      final PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }
    return psiClass instanceof PsiTypeParameter ? subst.substitute((PsiTypeParameter)psiClass) : elementFactory.createType(psiClass, substitutor);
  }

  public static void collectTypeParameters(final Set<PsiTypeParameter> used, @Nonnull final GroovyPsiElement element) {
    element.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitCodeReferenceElement(GrCodeReferenceElement reference) {
        super.visitCodeReferenceElement(reference);
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiTypeParameter) {
            final PsiTypeParameter typeParameter = (PsiTypeParameter)resolved;
            if (PsiTreeUtil.isAncestor(typeParameter.getOwner(), element, false)) {
              used.add(typeParameter);
            }
          }
        }
      }

      @Override
      public void visitExpression(final GrExpression expression) {
        super.visitExpression(expression);
        final PsiType type = expression.getType();
        if (type != null) {
          final TypeParameterSearcher searcher = new TypeParameterSearcher();
          type.accept(searcher);
          for (PsiTypeParameter typeParam : searcher.myTypeParams) {
            if (PsiTreeUtil.isAncestor(typeParam.getOwner(), element, false)) {
              used.add(typeParam);
            }
          }
        }
      }

      class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
        private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

        public Boolean visitType(final PsiType type) {
          return false;
        }

        public Boolean visitArrayType(final PsiArrayType arrayType) {
          return arrayType.getComponentType().accept(this);
        }

        public Boolean visitClassType(final PsiClassType classType) {
          final PsiClass aClass = classType.resolve();
          if (aClass instanceof PsiTypeParameter) {
            myTypeParams.add((PsiTypeParameter)aClass);
          }

          final PsiType[] types = classType.getParameters();
          for (final PsiType psiType : types) {
            psiType.accept(this);
          }
          return false;
        }

        public Boolean visitWildcardType(final PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            bound.accept(this);
          }
          return false;
        }
      }
    });
  }
}
