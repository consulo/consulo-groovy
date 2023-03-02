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
package org.jetbrains.plugins.groovy.impl.testIntegration;

import com.intellij.java.impl.codeInsight.CodeInsightUtil;
import com.intellij.java.impl.refactoring.util.classMembers.MemberInfo;
import com.intellij.java.impl.testIntegration.TestIntegrationUtils;
import com.intellij.java.impl.testIntegration.createTest.CreateTestDialog;
import com.intellij.java.impl.testIntegration.createTest.TestGenerator;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyTestGenerator implements TestGenerator {

  @Nullable
  @Override
  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    AccessToken accessToken = WriteAction.start();
    try {
      final PsiClass test = (PsiClass)PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
        new Computable<PsiElement>() {
          public PsiElement compute() {
            try {
              consulo.ide.impl.idea.openapi.fileEditor.ex.IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

              GrTypeDefinition targetClass = CreateClassActionBase.createClassByType(
                d.getTargetDirectory(),
                d.getClassName(),
                PsiManager.getInstance(project),
                null,
                GroovyTemplates.GROOVY_CLASS, true);
              if (targetClass == null) return null;

              addSuperClass(targetClass, project, d.getSuperClassName());

              Editor editor = CodeInsightUtil.positionCursor(project, targetClass.getContainingFile(), targetClass.getLBrace());
              addTestMethods(editor,
                             targetClass,
                             d.getSelectedTestFrameworkDescriptor(),
                             d.getSelectedMethods(),
                             d.shouldGeneratedBefore(),
                             d.shouldGeneratedAfter());
              return targetClass;
            }
            catch (IncorrectOperationException e1) {
              showErrorLater(project, d.getClassName());
              return null;
            }
          }
        });
      if (test == null) return null;
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(test);
      CodeStyleManager.getInstance(project).reformat(test);
      return test;
    }
    finally {
      accessToken.finish();
    }
  }

  @Override
  public String toString() {
    return GroovyIntentionsBundle.message("intention.crete.test.groovy");
  }

  private static void addSuperClass(@Nonnull GrTypeDefinition targetClass, @Nonnull Project project, @Nullable String superClassName)
    throws IncorrectOperationException {
    if (superClassName == null) return;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    PsiClass superClass = findClass(project, superClassName);
    GrCodeReferenceElement superClassRef;
    if (superClass != null) {
      superClassRef = factory.createCodeReferenceElementFromClass(superClass);
    }
    else {
      superClassRef = factory.createCodeReferenceElementFromText(superClassName);
    }
    GrExtendsClause extendsClause = targetClass.getExtendsClause();
    if (extendsClause == null) {
      extendsClause = (GrExtendsClause)targetClass.addAfter(factory.createExtendsClause(), targetClass.getNameIdentifierGroovy());
    }

    extendsClause.add(superClassRef);
  }

  @Nullable
  private static PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private static void addTestMethods(Editor editor,
                                     PsiClass targetClass,
                                     TestFramework descriptor,
                                     Collection<MemberInfo> methods,
                                     boolean generateBefore,
                                     boolean generateAfter) throws IncorrectOperationException {
    if (generateBefore) {
      generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, editor, null);
    }
    if (generateAfter) {
      generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, editor, null);
    }
    for (MemberInfo m : methods) {
      generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, editor, m.getMember().getName());
    }
  }

  private static void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        Messages.showErrorDialog(project,
                                 CodeInsightBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                 CodeInsightBundle.message("intention.error.cannot.create.class.title"));
      }
    });
  }

  private static void generateMethod(TestIntegrationUtils.MethodKind methodKind,
                                     TestFramework descriptor,
                                     PsiClass targetClass,
                                     Editor editor,
                                     @Nullable String name) {
    GroovyPsiElementFactory f = GroovyPsiElementFactory.getInstance(targetClass.getProject());
    PsiMethod method = (PsiMethod)targetClass.add(f.createMethod("dummy", PsiType.VOID));
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, method, name, true);
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
