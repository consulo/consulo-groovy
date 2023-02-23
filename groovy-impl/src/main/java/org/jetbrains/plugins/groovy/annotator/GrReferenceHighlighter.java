/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator;

import consulo.application.progress.ProgressIndicator;
import consulo.document.Document;
import consulo.language.editor.impl.highlight.TextEditorHighlightingPass;
import consulo.language.editor.impl.highlight.UpdateHighlightersUtil;
import consulo.language.editor.rawHighlight.HighlightInfo;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrReferenceHighlighter extends TextEditorHighlightingPass {
  private final GroovyFileBase myFile;
  private List<HighlightInfo> myInfos = null;

  public GrReferenceHighlighter(@Nullable Document document, @Nonnull GroovyFileBase file) {
    super(file.getProject(), document);
    myFile = file;
  }

  @Override
  public void doCollectInformation(@Nonnull ProgressIndicator progress) {
    myInfos = new ArrayList<HighlightInfo>();

    myFile.accept(new GrDeclarationHighlightingVisitor(myInfos));
    myFile.accept(new ResolveHighlightingVisitor(myFile, myProject, myInfos));
    myFile.accept(new InaccessibleElementVisitor(myFile, myProject, myInfos));
  }

  @Override
  public void doApplyInformationToEditor() {
    if (myInfos == null || myDocument == null) {
      return;
    }
    UpdateHighlightersUtil.setHighlightersToEditor(myProject,
																									 myDocument,
																									 0,
																									 myFile.getTextLength(),
																									 myInfos,
																									 getColorsScheme(),
																									 getId());
  }
}
