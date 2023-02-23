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
package org.jetbrains.plugins.groovy.intentions.base;

import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.java.impl.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix;
import com.intellij.java.language.psi.*;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.impl.internal.template.TemplateBuilderImpl;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.editor.template.event.TemplateEditingListener;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;
import org.jetbrains.plugins.groovy.template.expressions.ParameterNameExpression;

import javax.annotation.Nonnull;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2007
 */
public class IntentionUtils {

  private static final Logger LOG = Logger.getInstance(IntentionUtils.class);

  public static void createTemplateForMethod(PsiType[] argTypes,
                                             ChooseTypeExpression[] paramTypesExpressions,
                                             PsiMethod method,
                                             PsiClass owner,
                                             TypeConstraint[] constraints,
                                             boolean isConstructor,
                                             @Nonnull final PsiElement context) {

    final Project project = owner.getProject();
    PsiTypeElement typeElement = method.getReturnTypeElement();
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project),
                                                         context.getResolveScope(), method.getLanguage() == GroovyLanguage.INSTANCE);
    TemplateBuilderImpl builder = new TemplateBuilderImpl(method);
    if (!isConstructor) {
      assert typeElement != null;
      builder.replaceElement(typeElement, expr);
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    assert parameters.length == argTypes.length;
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      PsiTypeElement parameterTypeElement = parameter.getTypeElement();
      builder.replaceElement(parameterTypeElement, paramTypesExpressions[i]);
      builder.replaceElement(parameter.getNameIdentifier(), new ParameterNameExpression(null));
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiElement lbrace = body.getLBrace();
      assert lbrace != null;
      builder.setEndVariableAfter(lbrace);
    }
    else {
      builder.setEndVariableAfter(method.getParameterList());
    }

    method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
    Template template = builder.buildTemplate();

    final PsiFile targetFile = owner.getContainingFile();
    final Editor newEditor = positionCursor(project, targetFile, method);
    TextRange range = method.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);


    TemplateEditingListener templateListener = new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
            final int offset = newEditor.getCaretModel().getOffset();
            PsiMethod method = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset - 1,
                                                                      PsiMethod.class, false);
            if (context instanceof PsiMethod) {
              final PsiTypeParameter[] typeParameters = ((PsiMethod)context).getTypeParameters();
              if (typeParameters.length > 0) {
                for (PsiTypeParameter typeParameter : typeParameters) {
                  if (CreateMethodFromUsageFix.checkTypeParam(method, typeParameter)) {
                    final JVMElementFactory factory = JVMElementFactories.getFactory(method
                                                                                       .getLanguage(), method.getProject());
                    PsiTypeParameterList list = method.getTypeParameterList();
                    if (list == null) {
                      PsiTypeParameterList newList = factory.createTypeParameterList();
                      list = (PsiTypeParameterList)method.addAfter(newList,
                                                                   method.getModifierList());
                    }
                    list.add(factory.createTypeParameter(typeParameter.getName(),
                                                         typeParameter.getExtendsList().getReferencedTypes()));
                  }
                }
              }
            }
            if (method != null) {
              try {
                final boolean hasNoReturnType = method.getReturnTypeElement() == null && method
                  instanceof GrMethod;
                if (hasNoReturnType) {
                  ((GrMethod)method).setReturnType(PsiType.VOID);
                }
                if (method.getBody() != null) {
                  FileTemplateManager templateManager = FileTemplateManager.getInstance();
                  FileTemplate fileTemplate = templateManager.getCodeTemplate(GroovyTemplates
                                                                                .GROOVY_FROM_USAGE_METHOD_BODY);

                  PsiClass containingClass = method.getContainingClass();
                  LOG.assertTrue(!containingClass.isInterface() || GrTraitUtil.isTrait
                    (containingClass), "Interface bodies should be already set up");
                  CreateFromUsageUtils.setupMethodBody(method, containingClass, fileTemplate);
                }
                if (hasNoReturnType) {
                  ((GrMethod)method).setReturnType(null);
                }
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }

              CreateFromUsageUtils.setupEditor(method, newEditor);
            }
          }
        });
      }
    };
    manager.startTemplate(newEditor, template, templateListener);
  }

  public static Editor positionCursor(@Nonnull Project project,
                                      @Nonnull PsiFile targetFile,
                                      @Nonnull PsiElement element) {
    int textOffset = element.getTextOffset();
    VirtualFile virtualFile = targetFile.getVirtualFile();
    if (virtualFile != null) {
      OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(virtualFile).offset(textOffset).build();
      return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    else {
      return null;
    }
  }
}
