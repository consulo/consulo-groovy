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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.MoveClassToSeparateFileFix;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaFile;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.application.ApplicationManager;
import consulo.document.Document;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.io.FileUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaProcessor extends BaseRefactoringProcessor {
  private static Logger LOG = Logger.getInstance(ConvertToJavaProcessor.class);

  private GroovyFile[] myFiles;

  protected ConvertToJavaProcessor(Project project, GroovyFile... files) {
    super(project);
    myFiles = files;
  }

  @Nonnull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @Nonnull
      @Override
      public PsiElement[] getElements() {
        return myFiles;
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("files.to.be.converted");
      }
    };
  }

  @Nonnull
  @Override
  protected UsageInfo[] findUsages() {
    return UsageInfo.EMPTY_ARRAY;
  }

  //private static String
  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    final GeneratorClassNameProvider classNameProvider = new GeneratorClassNameProvider();

    ExpressionContext context = new ExpressionContext(myProject, myFiles);
    final ClassGenerator classGenerator = new ClassGenerator(classNameProvider, new ClassItemGeneratorImpl(context));

    for (GroovyFile file : myFiles) {
      final PsiClass[] classes = file.getClasses();
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (PsiClass aClass : classes) {
        classGenerator.writeTypeDefinition(builder, aClass, true, first);
        first = false;
        builder.append('\n');
      }

      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      LOG.assertTrue(document != null);
      document.setText(builder.toString());
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      String fileName = getNewFileName(file);
      PsiElement newFile;
      try {
        newFile = file.setName(fileName);
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showMessageDialog(myProject, e.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
          }
        });
        return;
      }

      doPostProcessing(newFile);
    }
  }

  private void doPostProcessing(PsiElement newFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    // don't move classes to new files with corresponding class names and reformat

    if (!(newFile instanceof PsiJavaFile)) {
      LOG.info(".java is not assigned to java file type");
      return;
    }

    newFile = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newFile);
    newFile = CodeStyleManager.getInstance(myProject).reformat(newFile);
    PsiClass[] inner = ((PsiJavaFile)newFile).getClasses();
    for (PsiClass psiClass : inner) {
      MoveClassToSeparateFileFix fix = new MoveClassToSeparateFileFix(psiClass);
      if (fix.isAvailable(myProject, null, (PsiFile)newFile)) {
        fix.invoke(myProject, null, (PsiFile)newFile);
      }
    }
  }

  private static String getNewFileName(GroovyFile file) {
    final PsiDirectory dir = file.getContainingDirectory();
    LOG.assertTrue(dir != null);


    final PsiFile[] files = dir.getFiles();
    Set<String> fileNames = new HashSet<String>();
    for (PsiFile psiFile : files) {
      fileNames.add(psiFile.getName());
    }
    String prefix = FileUtil.getNameWithoutExtension(file.getName());
    String fileName = prefix + ".java";
    int index = 1;
    while (fileNames.contains(fileName)) {
      fileName = prefix + index + ".java";
    }
    return fileName;
  }

  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.java");
  }
}
