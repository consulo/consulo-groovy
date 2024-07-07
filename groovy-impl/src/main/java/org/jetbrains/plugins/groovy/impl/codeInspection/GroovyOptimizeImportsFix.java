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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.AutoImportHelper;
import consulo.language.editor.CodeInsightSettings;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.intention.SyntheticIntentionAction;
import consulo.language.editor.rawHighlight.HighlightInfoType;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UndoManager;
import consulo.undoRedo.util.UndoUtil;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.impl.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;

import jakarta.annotation.Nonnull;

public class GroovyOptimizeImportsFix implements SyntheticIntentionAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.codeInspection.local" +
                                                         ".GroovyPostHighlightingPass");
  private final boolean onTheFly;

  public GroovyOptimizeImportsFix(boolean onTheFly) {
    this.onTheFly = onTheFly;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final Runnable optimize = new GroovyImportOptimizer().processFile(file);
    GroovyOptimizeImportsFix.invokeOnTheFlyImportOptimizer(optimize, file, editor);
  }

  @Override
  @Nonnull
  public String getText() {
    return GroovyInspectionBundle.message("optimize.all.imports");
  }

  //@Override
  @Nonnull
  public String getFamilyName() {
    return GroovyInspectionBundle.message("optimize.imports");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return file instanceof GroovyFile && (!onTheFly || timeToOptimizeImports((GroovyFile)file, editor));
  }

  private boolean timeToOptimizeImports(GroovyFile file, Editor editor) {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) {
      return false;
    }
    Project project = file.getProject();
    if (onTheFly && editor != null) {
      // if we stand inside import statements, do not optimize
      final VirtualFile vfile = file.getVirtualFile();
      if (vfile != null && ProjectRootManager.getInstance(project).getFileIndex().isInSource(vfile)) {
        final GrImportStatement[] imports = file.getImportStatements();
        if (imports.length > 0) {
          final int offset = editor.getCaretModel().getOffset();
          if (imports[0].getTextRange().getStartOffset() <= offset && offset <= imports[imports.length - 1]
            .getTextRange().getEndOffset()) {
            return false;
          }
        }
      }
    }

    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    if (!codeAnalyzer.isHighlightingAvailable(file)) {
      return false;
    }

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) {
      return false;
    }
    Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    boolean errors = containsErrorsPreventingOptimize(file, document);

    return !errors && AutoImportHelper.getInstance(project).canChangeFileSilently(file);
  }

  private boolean containsErrorsPreventingOptimize(GroovyFile file, Document document) {
    // ignore unresolved imports errors
    final TextRange ignoreRange;
    final GrImportStatement[] imports = file.getImportStatements();
    if (imports.length != 0) {
      final int start = imports[0].getTextRange().getStartOffset();
      final int end = imports[imports.length - 1].getTextRange().getEndOffset();
      ignoreRange = new TextRange(start, end);
    }
    else {
      ignoreRange = TextRange.EMPTY_RANGE;
    }

    return !DaemonCodeAnalyzer.processHighlights(document,
                                                 file.getProject(),
                                                 HighlightSeverity.ERROR,
                                                 0,
                                                 document.getTextLength(),
                                                 error -> {
                                                   int infoStart = error.getActualStartOffset();
                                                   int infoEnd = error.getActualEndOffset();

                                                   return ignoreRange.containsRange(
                                                     infoStart,
                                                     infoEnd) && error.getType().equals(
                                                     HighlightInfoType.WRONG_REF);
                                                 });
  }

  public static void invokeOnTheFlyImportOptimizer(@Nonnull final Runnable runnable,
                                                   @Nonnull final PsiFile file,
                                                   @Nonnull final Editor editor) {
    final long stamp = editor.getDocument().getModificationStamp();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (file.getProject().isDisposed() || editor.isDisposed() || editor.getDocument().getModificationStamp() != stamp) {
          return;
        }
        //no need to optimize imports on the fly during undo/redo
        final UndoManager undoManager = ProjectUndoManager.getInstance(editor.getProject());
        if (undoManager.isUndoInProgress() || undoManager.isRedoInProgress()) {
          return;
        }
        PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
        String beforeText = file.getText();
        final long oldStamp = editor.getDocument().getModificationStamp();
        UndoUtil.writeInRunUndoTransparentAction(runnable);
        if (oldStamp != editor.getDocument().getModificationStamp()) {
          String afterText = file.getText();
          if (Comparing.strEqual(beforeText, afterText)) {
            String path = file.getViewProvider().getVirtualFile().getPath();
            LOG.error("Import optimizer  hasn't optimized any imports", AttachmentFactory.get().create(path, afterText));
          }
        }
      }
    });
  }
}
