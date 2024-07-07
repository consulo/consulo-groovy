/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 30-Nov-2009
 */
package org.jetbrains.plugins.groovy.impl.copyright;

import com.intellij.java.impl.copyright.psi.UpdateJavaFileCopyright;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.copyright.UpdateCopyrightsProvider;
import consulo.language.copyright.UpdatePsiFileCopyright;
import consulo.language.copyright.config.CopyrightFileConfig;
import consulo.language.copyright.config.CopyrightProfile;
import consulo.language.copyright.ui.TemplateCommentPanel;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import jakarta.annotation.Nonnull;
import javax.swing.*;
import java.util.List;

@ExtensionImpl
public class UpdateGroovyCopyrightsProvider extends UpdateCopyrightsProvider<CopyrightFileConfig> {
  @Nonnull
  @Override
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Nonnull
  @Override
  public UpdatePsiFileCopyright<CopyrightFileConfig> createInstance(@Nonnull PsiFile file, @Nonnull CopyrightProfile copyrightProfile) {
    return new UpdateJavaFileCopyright(file, copyrightProfile) {
      @Override
      protected boolean accept() {
        return getFile() instanceof GroovyFile;
      }

      @Override
      protected PsiElement[] getImportsList() {
        return ((GroovyFile)getFile()).getImportStatements();
      }

      @Override
      protected PsiElement getPackageStatement() {
        return ((GroovyFile)getFile()).getPackageDefinition();
      }

      @Override
      protected void checkCommentsForTopClass(PsiClass topclass, int location, List<PsiComment> comments) {
        if (!(topclass instanceof GroovyScriptClass)) {
          super.checkCommentsForTopClass(topclass, location, comments);
          return;
        }
        final GroovyFile containingFile = (GroovyFile)topclass.getContainingFile();

        PsiElement last = containingFile.getFirstChild();
        while (last != null && !(last instanceof GrStatement)) {
          last = last.getNextSibling();
        }
        checkComments(last, location == LOCATION_BEFORE_CLASS, comments);
      }
    };
  }

  @Nonnull
  @Override
  public CopyrightFileConfig createDefaultOptions() {
    return new CopyrightFileConfig();
  }

  @Nonnull
  @Override
  public TemplateCommentPanel createConfigurable(@Nonnull Project project,
                                                 @Nonnull TemplateCommentPanel parentPane,
                                                 @Nonnull FileType fileType) {
    return new TemplateCommentPanel(fileType, parentPane, project) {
      @Override
      public void addAdditionalComponents(@Nonnull JPanel additionalPanel) {
        addLocationInFile(new String[]{
          "Before Package",
          "Before Imports",
          "Before Class"
        });
      }
    };
  }
}