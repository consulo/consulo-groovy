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

package org.jetbrains.plugins.groovy.impl.refactoring.extract.method;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.codeEditor.SelectionModel;
import consulo.dataContext.DataContext;
import consulo.document.util.TextRange;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.IntroduceTargetChooser;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.collection.MultiMap;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GrRefactoringError;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.GroovyExtractChooser;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.InitialInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ParameterInfo;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceHandlerBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author ilyas
 */
public class GroovyExtractMethodHandler implements RefactoringActionHandler {
  protected static String REFACTORING_NAME = GroovyRefactoringBundle.message("extract.method.title");
  private static final Logger LOG = Logger.getInstance(GroovyExtractMethodHandler.class);

  public void invoke(@Nonnull final Project project, final Editor editor, final PsiFile file, @Nullable DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final SelectionModel model = editor.getSelectionModel();
    if (model.hasSelection()) {
      invokeImpl(project, editor, file, model.getSelectionStart(), model.getSelectionEnd());
    }
    else {
      final List<GrExpression> expressions =
        GrIntroduceHandlerBase.collectExpressions(file, editor, editor.getCaretModel().getOffset(), true);
      final Consumer<GrExpression> callback = new Callback(project, editor, file);
      if (expressions.size() == 1) {
        callback.accept(expressions.get(0));
      }
      else if (expressions.isEmpty()) {
        model.selectLineAtCaret();
        invokeImpl(project, editor, file, model.getSelectionStart(), model.getSelectionEnd());
      }
      else {
        IntroduceTargetChooser.showChooser(editor, expressions, callback, GrIntroduceHandlerBase.GR_EXPRESSION_RENDERER);
      }
    }
  }

  private class Callback implements Consumer<GrExpression> {
    private final Project project;
    private final Editor editor;
    private final PsiFile file;


    private Callback(Project project, Editor editor, PsiFile file) {
      this.project = project;
      this.editor = editor;
      this.file = file;
    }

    public void accept(@Nonnull final GrExpression selectedValue) {
      final TextRange range = selectedValue.getTextRange();
      invokeImpl(project, editor, file, range.getStartOffset(), range.getEndOffset());
    }
  }

  private void invokeImpl(Project project, Editor editor, PsiFile file, final int startOffset, final int endOffset) {
    try {
      final InitialInfo initialInfo = GroovyExtractChooser.invoke(project, editor, file, startOffset, endOffset, true);

      if (findConflicts(initialInfo)) {
        return;
      }

      performRefactoring(initialInfo, editor);
    }
    catch (GrRefactoringError e) {
      CommonRefactoringUtil.showErrorHint(project, editor, e.getMessage(), REFACTORING_NAME, HelpID.EXTRACT_METHOD);
    }
  }

  private static boolean findConflicts(InitialInfo info) {
    //new ConflictsDialog()
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    final PsiElement declarationOwner = info.getStatements()[0].getParent();

    GroovyRecursiveElementVisitor visitor = new GroovyRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
        super.visitReferenceExpression(referenceExpression);

        GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
        PsiElement resolveContext = resolveResult.getCurrentFileResolveContext();
        if (resolveContext != null && !(resolveContext instanceof GrImportStatement) && !PsiTreeUtil.isAncestor(declarationOwner,
                                                                                                                resolveContext,
                                                                                                                true) && !skipResult(
          resolveResult)) {
          conflicts.putValue(referenceExpression,
                             GroovyRefactoringBundle.message("ref.0.will.not.be.resolved.outside.of.current.context",
                                                             referenceExpression.getText()));
        }
      }

      //skip 'print' and 'println'
      private boolean skipResult(GroovyResolveResult result) {
        PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          String name = ((PsiMethod)element).getName();
          if (!name.startsWith("print")) {
            return false;
          }

          if (element instanceof GrGdkMethod) {
            element = ((GrGdkMethod)element).getStaticMethod();
          }

          PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            String qname = aClass.getQualifiedName();
            return GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(qname);
          }
        }
        return false;
      }
    };

    GrStatement[] statements = info.getStatements();
    for (GrStatement statement : statements) {
      statement.accept(visitor);
    }

    if (conflicts.isEmpty()) {
      return false;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
    }

    ConflictsDialog dialog = new ConflictsDialog(info.getProject(), conflicts);
    dialog.show();
    return !dialog.isOK();
  }

  private void performRefactoring(@Nonnull final InitialInfo initialInfo, @Nullable final Editor editor) {
    final PsiClass owner = PsiUtil.getContextClass(initialInfo.getStatements()[0]);
    LOG.assertTrue(owner != null);

    final ExtractMethodInfoHelper helper = getSettings(initialInfo, owner);
    if (helper == null) {
      return;
    }

    CommandProcessor.getInstance().executeCommand(helper.getProject(), new Runnable() {
      public void run() {
        WriteAction.run(() ->
                        {
                          createMethod(helper, owner);
                          GrStatementOwner declarationOwner = GroovyRefactoringUtil.getDeclarationOwner(helper.getStatements()[0]);
                          GrStatement realStatement = ExtractUtil.replaceStatement(declarationOwner, helper);

                          // move to offset
                          if (editor != null) {
                            PsiDocumentManager.getInstance(helper.getProject()).commitDocument(editor.getDocument());
                            editor.getSelectionModel().removeSelection();
                            editor.getCaretModel().moveToOffset(ExtractUtil.getCaretOffset(realStatement));
                          }
                        });
      }
    }, REFACTORING_NAME, null);
  }

  private static void createMethod(ExtractMethodInfoHelper helper, PsiClass owner) {
    final GrMethod method = ExtractUtil.createMethod(helper);
    PsiElement anchor = calculateAnchorToInsertBefore(owner, helper.getStatements()[0]);
    GrMethod newMethod = (GrMethod)owner.addBefore(method, anchor);
    renameParameterOccurrences(newMethod, helper);
    JavaCodeStyleManager.getInstance(newMethod.getProject()).shortenClassReferences(newMethod);
    PsiElement prev = newMethod.getPrevSibling();
    if (!PsiUtil.isLineFeed(prev)) {
      newMethod.getParent().getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", newMethod.getNode());
    }
  }

  @Nullable
  protected ExtractMethodInfoHelper getSettings(@Nonnull InitialInfo initialInfo, PsiClass owner) {
    GroovyExtractMethodDialog dialog = new GroovyExtractMethodDialog(initialInfo, owner);
    dialog.show();
    if (!dialog.isOK()) {
      return null;
    }

    return dialog.getHelper();
  }


  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    // does nothing
  }

  @Nullable
  private static PsiElement calculateAnchorToInsertBefore(PsiClass owner, PsiElement startElement) {
    while (startElement != null && !isEnclosingDefinition(owner, startElement)) {
      if (startElement.getParent() instanceof GroovyFile) {
        return startElement.getNextSibling();
      }
      startElement = startElement.getParent();
      PsiElement parent = startElement.getParent();
      if (parent instanceof GroovyFile && ((GroovyFile)parent).getScriptClass() == owner) {
        return startElement.getNextSibling();
      }
    }
    return startElement == null ? null : startElement.getNextSibling();
  }

  private static boolean isEnclosingDefinition(PsiClass owner, PsiElement startElement) {
    if (owner instanceof GrTypeDefinition) {
      GrTypeDefinition definition = (GrTypeDefinition)owner;
      return startElement.getParent() == definition.getBody();
    }
    return false;
  }

  private static void renameParameterOccurrences(GrMethod method, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    GrOpenBlock block = method.getBlock();
    if (block == null) {
      return;
    }
    GrStatement[] statements = block.getStatements();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    for (ParameterInfo info : helper.getParameterInfos()) {
      final String oldName = info.getOldName();
      final String newName = info.getName();
      final ArrayList<GrExpression> result = new ArrayList<GrExpression>();
      if (!oldName.equals(newName)) {
        for (final GrStatement statement : statements) {
          statement.accept(new PsiRecursiveElementVisitor() {
            public void visitElement(final PsiElement element) {
              super.visitElement(element);
              if (element instanceof GrReferenceExpression) {
                GrReferenceExpression expr = (GrReferenceExpression)element;
                if (!expr.isQualified() && oldName.equals(expr.getReferenceName())) {
                  result.add(expr);
                }
              }
            }
          });
          for (GrExpression expr : result) {
            expr.replaceWithExpression(factory.createExpressionFromText(newName), false);
          }
        }
      }
    }
  }
}
