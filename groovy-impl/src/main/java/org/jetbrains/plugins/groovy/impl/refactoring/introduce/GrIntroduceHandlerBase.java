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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorColors;
import consulo.codeEditor.SelectionModel;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.highlight.HighlightManager;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.RefactoringSupportProvider;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.editor.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import consulo.language.editor.refactoring.introduce.inplace.OccurrencesChooser;
import consulo.language.editor.refactoring.rename.inplace.InplaceRefactoring;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.NameValidator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created by Max Medvedev on 10/29/13
 */
public abstract class GrIntroduceHandlerBase<Settings extends GrIntroduceSettings, Scope extends PsiElement> implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(GrIntroduceHandlerBase.class);

  public static final Function<GrExpression, String> GR_EXPRESSION_RENDERER = expr -> expr.getText();

  public static GrExpression insertExplicitCastIfNeeded(GrVariable variable, GrExpression initializer) {
    PsiType ltype = findLValueType(initializer);
    PsiType rtype = initializer.getType();

    GrExpression rawExpr = (GrExpression)PsiUtil.skipParentheses(initializer, false);

    if (ltype == null || TypesUtil.isAssignableWithoutConversions(ltype, rtype, initializer) || !TypesUtil.isAssignable(ltype,
                                                                                                                        rtype,
                                                                                                                        initializer)) {
      return rawExpr;
    }
    else { // implicit coercion should be replaced with explicit cast
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(variable.getProject());
      GrSafeCastExpression cast = (GrSafeCastExpression)factory.createExpressionFromText("a as B");
      cast.getOperand().replaceWithExpression(rawExpr, false);
      cast.getCastTypeElement().replace(factory.createTypeElement(ltype));
      return cast;
    }
  }

  @Nullable
  private static PsiType findLValueType(GrExpression initializer) {
    if (initializer.getParent() instanceof GrAssignmentExpression && ((GrAssignmentExpression)initializer.getParent()).getRValue() == initializer) {
      return ((GrAssignmentExpression)initializer.getParent()).getLValue().getNominalType();
    }
    else if (initializer.getParent() instanceof GrVariable) {
      return ((GrVariable)initializer.getParent()).getDeclaredType();
    }
    else {
      return null;
    }
  }

  @Nonnull
  public static GrStatement getAnchor(@Nonnull PsiElement[] occurrences, @Nonnull PsiElement scope) {
    PsiElement parent = PsiTreeUtil.findCommonParent(occurrences);
    PsiElement container = getEnclosingContainer(parent);
    assert container != null;
    PsiElement anchor = findAnchor(occurrences, container);

    assertStatement(anchor, scope);
    return (GrStatement)anchor;
  }

  @Nullable
  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place;
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

  @Nonnull
  protected abstract String getRefactoringName();

  @Nonnull
  protected abstract String getHelpID();

  @Nonnull
  protected abstract Scope[] findPossibleScopes(GrExpression expression, GrVariable variable, StringPartInfo stringPart, Editor editor);

  protected abstract void checkExpression(@Nonnull GrExpression selectedExpr) throws GrRefactoringError;

  protected abstract void checkVariable(@Nonnull GrVariable variable) throws GrRefactoringError;

  protected abstract void checkStringLiteral(@Nonnull StringPartInfo info) throws GrRefactoringError;

  protected abstract void checkOccurrences(@Nonnull PsiElement[] occurrences);

  @Nonnull
  protected abstract GrIntroduceDialog<Settings> getDialog(@Nonnull GrIntroduceContext context);

  @Nullable
  public abstract GrVariable runRefactoring(@Nonnull GrIntroduceContext context, @Nonnull Settings settings);

  protected abstract GrAbstractInplaceIntroducer<Settings> getIntroducer(@Nonnull GrIntroduceContext context,
                                                                         OccurrencesChooser.ReplaceChoice choice);

  public static Map<OccurrencesChooser.ReplaceChoice, List<Object>> fillChoice(GrIntroduceContext context) {
    HashMap<OccurrencesChooser.ReplaceChoice, List<Object>> map = new LinkedHashMap<>();

    if (context.getExpression() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getExpression()));
    }
    else if (context.getStringPart() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.NO, Collections.<Object>singletonList(context.getStringPart()));
      return map;
    }
    else if (context.getVar() != null) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Collections.<Object>singletonList(context.getVar()));
      return map;
    }

    PsiElement[] occurrences = context.getOccurrences();
    if (occurrences.length > 1) {
      map.put(OccurrencesChooser.ReplaceChoice.ALL, Arrays.<Object>asList(occurrences));
    }
    return map;
  }

  @Nonnull
  public static List<GrExpression> collectExpressions(PsiFile file, Editor editor, int offset, boolean acceptVoidCalls) {
    int correctedOffset = correctOffset(editor, offset);
    PsiElement elementAtCaret = file.findElementAt(correctedOffset);
    return collectExpressions(elementAtCaret, acceptVoidCalls);
  }

  @Nonnull
  public static List<GrExpression> collectExpressions(PsiElement elementAtCaret, boolean acceptVoidCalls) {
    List<GrExpression> expressions = new ArrayList<GrExpression>();

    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class); expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (expressions.contains(expression)) {
        continue;
      }
      if (expression instanceof GrParenthesizedExpression && !expressions.contains(((GrParenthesizedExpression)expression).getOperand())) {
        expressions.add(((GrParenthesizedExpression)expression).getOperand());
      }
      if (expressionIsIncorrect(expression, acceptVoidCalls)) {
        continue;
      }

      expressions.add(expression);
    }
    return expressions;
  }

  public static boolean expressionIsIncorrect(@Nullable GrExpression expression, boolean acceptVoidCalls) {
    if (expression instanceof GrParenthesizedExpression) {
      return true;
    }
    if (PsiUtil.isSuperReference(expression)) {
      return true;
    }
    if (expression instanceof GrAssignmentExpression) {
      return true;
    }
    if (expression instanceof GrReferenceExpression && expression.getParent() instanceof GrCall) {
      GroovyResolveResult resolveResult = ((GrReferenceExpression)expression).advancedResolve();
      PsiElement resolved = resolveResult.getElement();
      return resolved instanceof PsiMethod && !resolveResult.isInvokedOnProperty() || resolved instanceof PsiClass;
    }

    if (expression instanceof GrClosableBlock && expression.getParent() instanceof GrStringInjection) {
      return true;
    }
    if (!acceptVoidCalls && expression instanceof GrMethodCall && PsiType.VOID == expression.getType()) {
      return true;
    }

    return false;
  }

  public static int correctOffset(Editor editor, int offset) {
    Document document = editor.getDocument();
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!Character.isJavaIdentifierPart(text.charAt(offset))) {
      correctedOffset--;
    }

    if (correctedOffset < 0) {
      correctedOffset = offset;
    }
    else {
      char c = text.charAt(correctedOffset);
      if (c == ';' && correctedOffset != 0) {//initially caret on the end of line
        correctedOffset--;
      }
      else if (!Character.isJavaIdentifierPart(c) && c != ')' && c != ']' && c != '}' && c != '\'' && c != '"' && c != '/') {
        correctedOffset = offset;
      }
    }
    return correctedOffset;
  }

  @Nullable
  public static GrVariable findVariableAtCaret(PsiFile file, Editor editor, int offset) {
    int correctOffset = correctOffset(editor, offset);
    PsiElement elementAtCaret = file.findElementAt(correctOffset);
    GrVariable variable = PsiTreeUtil.getParentOfType(elementAtCaret, GrVariable.class);
    if (variable != null && variable.getNameIdentifierGroovy().getTextRange().contains(correctOffset)) {
      return variable;
    }
    return null;
  }

  @Override
  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, @Nullable DataContext dataContext) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      int offset = editor.getCaretModel().getOffset();

      List<GrExpression> expressions = collectExpressions(file, editor, offset, false);
      if (expressions.isEmpty()) {
        updateSelectionForVariable(editor, file, selectionModel, offset);
      }
      else if (expressions.size() == 1) {
        TextRange textRange = expressions.get(0).getTextRange();
        selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, new Consumer<GrExpression>() {
          @Override
          public void accept(GrExpression selectedValue) {
            invoke(project, editor, file, selectedValue.getTextRange().getStartOffset(), selectedValue.getTextRange().getEndOffset());
          }
        }, GR_EXPRESSION_RENDERER);
        return;
      }
    }
    invoke(project, editor, file, selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
  }

  public static void updateSelectionForVariable(Editor editor, PsiFile file, SelectionModel selectionModel, int offset) {
    GrVariable variable = findVariableAtCaret(file, editor, offset);
    if (variable == null || variable instanceof GrField || variable instanceof GrParameter) {
      selectionModel.selectLineAtCaret();
    }
    else {
      TextRange textRange = variable.getTextRange();
      selectionModel.setSelection(textRange.getStartOffset(), textRange.getEndOffset());
    }
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    // Does nothing
  }

  public void getContextAndInvoke(@Nonnull final Project project,
                                  @Nonnull final Editor editor,
                                  @Nullable final GrExpression expression,
                                  @Nullable final GrVariable variable,
                                  @Nullable final StringPartInfo stringPart) {
    Scope[] scopes = findPossibleScopes(expression, variable, stringPart, editor);

    Consumer<Scope> callback = new Consumer<Scope>() {
      @Override
      public void accept(Scope scope) {
        GrIntroduceContext context = getContext(project, editor, expression, variable, stringPart, scope);
        invokeImpl(project, context, editor);
      }
    };

    if (scopes.length == 0) {
      CommonRefactoringUtil.showErrorHint(project,
                                          editor,
                                          RefactoringBundle.getCannotRefactorMessage(getRefactoringName() + "is not available in current scope"),
                                          getRefactoringName(),
                                          getHelpID());
    }
    else if (scopes.length == 1) {
      callback.accept(scopes[0]);
    }
    else {
      showScopeChooser(scopes, callback, editor);
    }
  }

  protected void extractStringPart(final Ref<GrIntroduceContext> ref) {
    CommandProcessor.getInstance().executeCommand(ref.get().getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            GrIntroduceContext context = ref.get();

            StringPartInfo stringPart = context.getStringPart();
            assert stringPart != null;

            GrExpression expression = stringPart.replaceLiteralWithConcatenation(null);

            ref.set(new GrIntroduceContextImpl(context.getProject(),
                                               context.getEditor(),
                                               expression,
                                               null,
                                               null,
                                               new PsiElement[]{expression},
                                               context.getScope()));
          }
        });
      }
    }, getRefactoringName(), getRefactoringName());
  }

  protected void addBraces(@Nonnull final GrStatement anchor, @Nonnull final Ref<GrIntroduceContext> contextRef) {
    CommandProcessor.getInstance().executeCommand(contextRef.get().getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            GrIntroduceContext context = contextRef.get();
            SmartPointerManager pointManager = SmartPointerManager.getInstance(context.getProject());
            SmartPsiElementPointer<GrExpression> expressionRef =
              context.getExpression() != null ? pointManager.createSmartPsiElementPointer(context.getExpression()) : null;
            SmartPsiElementPointer<GrVariable> varRef =
              context.getVar() != null ? pointManager.createSmartPsiElementPointer(context.getVar()) : null;

            SmartPsiElementPointer[] occurrencesRefs = new SmartPsiElementPointer[context.getOccurrences().length];
            PsiElement[] occurrences = context.getOccurrences();
            for (int i = 0; i < occurrences.length; i++) {
              occurrencesRefs[i] = pointManager.createSmartPsiElementPointer(occurrences[i]);
            }


            PsiFile file = anchor.getContainingFile();
            SmartPsiFileRange anchorPointer = pointManager.createSmartPsiFileRangePointer(file, anchor.getTextRange());

            Document document = context.getEditor().getDocument();
            CharSequence sequence = document.getCharsSequence();

            TextRange range = anchor.getTextRange();

            int end = range.getEndOffset();
            document.insertString(end, "\n}");

            int start = range.getStartOffset();
            while (start > 0 && Character.isWhitespace(sequence.charAt(start - 1))) {
              start--;
            }
            document.insertString(start, "{");

            PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);

            Segment anchorSegment = anchorPointer.getRange();
            PsiElement restoredAnchor =
              PsiImplUtil.findElementInRange(file, anchorSegment.getStartOffset(), anchorSegment.getEndOffset(), PsiElement.class);
            GrCodeBlock block = (GrCodeBlock)restoredAnchor.getParent();
            CodeStyleManager.getInstance(context.getProject()).reformat(block.getRBrace());
            CodeStyleManager.getInstance(context.getProject()).reformat(block.getLBrace());

            for (int i = 0; i < occurrencesRefs.length; i++) {
              occurrences[i] = occurrencesRefs[i].getElement();
            }

            contextRef.set(new GrIntroduceContextImpl(context.getProject(),
                                                      context.getEditor(),
                                                      expressionRef != null ? expressionRef.getElement() : null,
                                                      varRef != null ? varRef.getElement() : null,
                                                      null,
                                                      occurrences,
                                                      context.getScope()));
          }
        });
      }
    }, getRefactoringName(), getRefactoringName());
  }

  @Nonnull
  protected static GrStatement findAnchor(@Nonnull final GrIntroduceContext context, final boolean replaceAll) {
    return ApplicationManager.getApplication().runReadAction(new Computable<GrStatement>() {
      @Override
      public GrStatement compute() {
        PsiElement[] occurrences = replaceAll ? context.getOccurrences() : new GrExpression[]{context.getExpression()};
        return getAnchor(occurrences, context.getScope());
      }
    });
  }

  protected abstract void showScopeChooser(Scope[] scopes, Consumer<Scope> callback, Editor editor);

  public GrIntroduceContext getContext(@Nonnull Project project,
                                       @Nonnull Editor editor,
                                       @Nullable GrExpression expression,
                                       @Nullable GrVariable variable,
                                       @Nullable StringPartInfo stringPart,
                                       @Nonnull PsiElement scope) {
    if (variable != null) {
      PsiElement[] occurrences = collectVariableUsages(variable, scope);
      return new GrIntroduceContextImpl(project, editor, null, variable, stringPart, occurrences, scope);
    }
    else if (expression != null) {
      PsiElement[] occurrences = findOccurrences(expression, scope);
      return new GrIntroduceContextImpl(project, editor, expression, variable, stringPart, occurrences, scope);
    }
    else {
      assert stringPart != null;
      return new GrIntroduceContextImpl(project,
                                        editor,
                                        expression,
                                        variable,
                                        stringPart,
                                        new PsiElement[]{stringPart.getLiteral()},
                                        scope);
    }
  }

  public static PsiElement[] collectVariableUsages(GrVariable variable, PsiElement scope) {
    final List<PsiElement> list = Collections.synchronizedList(new ArrayList<PsiElement>());
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    ReferencesSearch.search(variable, new LocalSearchScope(scope)).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference psiReference) {
        PsiElement element = psiReference.getElement();
        if (element != null) {
          list.add(element);
        }
        return true;
      }
    });
    return list.toArray(new PsiElement[list.size()]);
  }

  private boolean invokeImpl(Project project, final GrIntroduceContext context, Editor editor) {
    try {
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, context.getOccurrences())) {
        return false;
      }
      checkOccurrences(context.getOccurrences());


      if (isInplace(context.getEditor(), context.getPlace())) {
        Map<OccurrencesChooser.ReplaceChoice, List<Object>> occurrencesMap = getOccurrenceOptions(context);
        new IntroduceOccurrencesChooser(editor).showChooser(new Consumer<OccurrencesChooser.ReplaceChoice>() {
          @Override
          public void accept(OccurrencesChooser.ReplaceChoice choice) {
            getIntroducer(context, choice).startInplaceIntroduceTemplate();
          }
        }, occurrencesMap);
      }
      else {
        final Settings settings = showDialog(context);
        if (settings == null) {
          return false;
        }

        CommandProcessor.getInstance().executeCommand(context.getProject(), new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                runRefactoring(context, settings);
              }
            });
          }
        }, getRefactoringName(), null);
      }

      return true;
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project,
                                          editor,
                                          RefactoringBundle.getCannotRefactorMessage(e.getMessage()),
                                          getRefactoringName(),
                                          getHelpID());
      return false;
    }
  }

  @Nonnull
  protected Map<OccurrencesChooser.ReplaceChoice, List<Object>> getOccurrenceOptions(@Nonnull GrIntroduceContext context) {
    return fillChoice(context);
  }

  @Nonnull
  protected PsiElement[] findOccurrences(@Nonnull GrExpression expression, @Nonnull PsiElement scope) {
    PsiElement[] occurrences = GroovyRefactoringUtil.getExpressionOccurrences(PsiUtil.skipParentheses(expression, false), scope);
    if (occurrences == null || occurrences.length == 0) {
      throw new GrRefactoringError(GroovyRefactoringBundle.message("no.occurrences.found"));
    }
    return occurrences;
  }

  private void invoke(@Nonnull Project project,
                      @Nonnull Editor editor,
                      @Nonnull PsiFile file,
                      int startOffset,
                      int endOffset) throws GrRefactoringError {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments();
      if (!(file instanceof GroovyFileBase)) {
        throw new GrRefactoringError(GroovyRefactoringBundle.message("only.in.groovy.files"));
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) {
        throw new GrRefactoringError(RefactoringBundle.message("readonly.occurences.found"));
      }

      GrExpression selectedExpr = findExpression(file, startOffset, endOffset);
      GrVariable variable = findVariable(file, startOffset, endOffset);
      StringPartInfo stringPart = StringPartInfo.findStringPart(file, startOffset, endOffset);
      if (variable != null) {
        checkVariable(variable);
      }
      else if (selectedExpr != null) {
        checkExpression(selectedExpr);
      }
      else if (stringPart != null) {
        checkStringLiteral(stringPart);
      }
      else {
        throw new GrRefactoringError(null);
      }

      getContextAndInvoke(project, editor, selectedExpr, variable, stringPart);
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project,
                                          editor,
                                          RefactoringBundle.getCannotRefactorMessage(e.getMessage()),
                                          getRefactoringName(),
                                          getHelpID());
    }
  }

  public static RangeMarker createRange(Document document, StringPartInfo part) {
    if (part == null) {
      return null;
    }
    TextRange range = part.getRange().shiftRight(part.getLiteral().getTextRange().getStartOffset());
    return document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);

  }

  @Nullable
  public static RangeMarker createRange(@Nonnull Document document, @Nullable PsiElement expression) {
    if (expression == null) {
      return null;
    }
    TextRange range = expression.getTextRange();
    return document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), false);
  }


  public static boolean isInplace(@Nonnull Editor editor, @Nonnull PsiElement place) {
    RefactoringSupportProvider supportProvider = RefactoringSupportProvider.forLanguage(place.getLanguage());
    return supportProvider != null &&
      (editor.getUserData(InplaceRefactoring.INTRODUCE_RESTART) == null || !editor.getUserData(InplaceRefactoring.INTRODUCE_RESTART)) &&
      editor.getUserData(AbstractInplaceIntroducer.ACTIVE_INTRODUCE) == null &&
      editor.getSettings().isVariableInplaceRenameEnabled() &&
      supportProvider.isInplaceIntroduceAvailable(place, place) &&
      !ApplicationManager.getApplication().isUnitTestMode();
  }

  @Nullable
  public static GrVariable findVariable(@Nonnull PsiFile file, int startOffset, int endOffset) {
    GrVariable var = PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrVariable.class);
    if (var == null) {
      GrVariableDeclaration variableDeclaration =
        PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrVariableDeclaration.class);
      if (variableDeclaration == null) {
        return null;
      }
      GrVariable[] variables = variableDeclaration.getVariables();
      if (variables.length == 1) {
        var = variables[0];
      }
    }
    if (var instanceof GrParameter || var instanceof GrField) {
      return null;
    }
    return var;
  }

  @Nullable
  public static GrVariable findVariable(@Nonnull GrStatement statement) {
    if (!(statement instanceof GrVariableDeclaration)) {
      return null;
    }
    GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)statement;
    GrVariable[] variables = variableDeclaration.getVariables();

    GrVariable var = null;
    if (variables.length == 1) {
      var = variables[0];
    }
    if (var instanceof GrParameter || var instanceof GrField) {
      return null;
    }
    return var;
  }


  @Nullable
  public static GrExpression findExpression(PsiFile file, int startOffset, int endOffset) {
    GrExpression selectedExpr = PsiImplUtil.findElementInRange(file, startOffset, endOffset, GrExpression.class);
    return findExpression(selectedExpr);
  }

  @Nullable
  public static GrExpression findExpression(GrStatement selectedExpr) {
    if (!(selectedExpr instanceof GrExpression)) {
      return null;
    }

    GrExpression selected = (GrExpression)selectedExpr;
    while (selected instanceof GrParenthesizedExpression) {
      selected = ((GrParenthesizedExpression)selected).getOperand();
    }

    return selected;
  }

  @Nullable
  private Settings showDialog(@Nonnull GrIntroduceContext context) {

    // Add occurrences highlighting
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (context.getEditor() != null) {
      highlightManager = HighlightManager.getInstance(context.getProject());
      if (context.getOccurrences().length > 1) {
        highlightManager.addOccurrenceHighlights(context.getEditor(), context.getOccurrences(), EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
      }
    }

    GrIntroduceDialog<Settings> dialog = getDialog(context);

    dialog.show();
    if (dialog.isOK()) {
      if (context.getEditor() != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(context.getEditor(), highlighter);
        }
      }
      return dialog.getSettings();
    }
    return null;
  }

  @Nullable
  public static PsiElement findAnchor(@Nonnull PsiElement[] occurrences, @Nonnull PsiElement container) {
    if (occurrences.length == 0) {
      return null;
    }

    PsiElement candidate;
    if (occurrences.length == 1) {
      candidate = findContainingStatement(occurrences[0]);
    }
    else {
      candidate = occurrences[0];
      while (candidate != null && candidate.getParent() != container) {
        candidate = candidate.getParent();
      }
    }

    GrStringInjection injection = PsiTreeUtil.getParentOfType(candidate, GrStringInjection.class);
    if (injection != null && !injection.getText().contains("\n")) {
      candidate = findContainingStatement(injection);
    }

    if (candidate == null) {
      return null;
    }

    if ((container instanceof GrWhileStatement) && candidate.equals(((GrWhileStatement)container).getCondition())) {
      return container;
    }
    if ((container instanceof GrIfStatement) && candidate.equals(((GrIfStatement)container).getCondition())) {
      return container;
    }
    if ((container instanceof GrForStatement) && candidate.equals(((GrForStatement)container).getClause())) {
      return container;
    }

    while (candidate instanceof GrIfStatement &&
      candidate.getParent() instanceof GrIfStatement &&
      ((GrIfStatement)candidate.getParent()).getElseBranch() == candidate) {
      candidate = candidate.getParent();
    }
    return candidate;
  }

  public static void assertStatement(@Nullable PsiElement anchor, @Nonnull PsiElement scope) {
    if (!(anchor instanceof GrStatement)) {
      LOG.error("cannot find anchor for variable", AttachmentFactory.get().create("scope.txt", scope.getText()));
    }
  }

  @Nullable
  private static PsiElement findContainingStatement(@Nullable PsiElement candidate) {
    while (candidate != null && (candidate.getParent() instanceof GrLabeledStatement || !(PsiUtil.isExpressionStatement(candidate)))) {
      candidate = candidate.getParent();
      if (candidate instanceof GrCaseLabel) {
        candidate = candidate.getParent();
      }
    }
    return candidate;
  }

  public static void deleteLocalVar(GrVariable var) {
    PsiElement parent = var.getParent();
    if (((GrVariableDeclaration)parent).getVariables().length == 1) {
      parent.delete();
    }
    else {
      GrExpression initializer = var.getInitializerGroovy();
      if (initializer != null) {
        initializer.delete(); //don't special check for tuple, but this line is for the tuple case
      }
      var.delete();
    }
  }

  @Nullable
  public static GrVariable resolveLocalVar(@Nonnull GrIntroduceContext context) {
    GrVariable var = context.getVar();
    if (var != null) {
      return var;
    }

    return resolveLocalVar(context.getExpression());
  }

  @Nullable
  public static GrVariable resolveLocalVar(@Nullable GrExpression expression) {
    if (expression instanceof GrReferenceExpression) {
      GrReferenceExpression ref = (GrReferenceExpression)expression;

      PsiElement resolved = ref.resolve();
      if (PsiUtil.isLocalVariable(resolved)) {
        return (GrVariable)resolved;
      }
      return null;
    }

    return null;
  }

  public static boolean hasLhs(@Nonnull PsiElement[] occurrences) {
    for (PsiElement element : occurrences) {
      if (element instanceof GrReferenceExpression) {
        if (PsiUtil.isLValue((GroovyPsiElement)element)) {
          return true;
        }
        if (ControlFlowUtils.isIncOrDecOperand((GrReferenceExpression)element)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nonnull
  public static PsiElement getCurrentPlace(@Nullable GrExpression expr, @Nullable GrVariable var, @Nullable StringPartInfo stringPartInfo) {
    if (var != null) {
      return var;
    }
    if (expr != null) {
      return expr;
    }
    if (stringPartInfo != null) {
      return stringPartInfo.getLiteral();
    }

    throw new IncorrectOperationException();
  }

  public interface Validator extends NameValidator {
    boolean isOK(GrIntroduceDialog dialog);
  }
}
