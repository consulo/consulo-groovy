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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClassType;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.impl.idea.codeInsight.CodeInsightUtilBase;
import consulo.language.editor.impl.internal.template.TemplateBuilderImpl;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.OpenFileDescriptor;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.template.expressions.ChooseTypeExpression;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author ven
 */
public class CreateLocalVariableFromUsageFix implements IntentionAction {
  private final GrVariableDeclarationOwner myOwner;
  private final GrReferenceExpression myRefExpression;

  public CreateLocalVariableFromUsageFix(GrReferenceExpression refExpression, GrVariableDeclarationOwner owner) {
    myRefExpression = refExpression;
    myOwner = owner;
  }

  @Nonnull
  public String getText() {
    return GroovyBundle.message("create.variable.from.usage", myRefExpression.getReferenceName());
  }

  @Nonnull
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    return myOwner.isValid() && myRefExpression.isValid();
  }

  @Nullable
  protected static Editor positionCursor(Project project, PsiFile targetFile, PsiElement element) {
    TextRange range = element.getTextRange();
    int textOffset = range.getStartOffset();

    VirtualFile vFile = targetFile.getVirtualFile();
    assert vFile != null;
    OpenFileDescriptor descriptor = OpenFileDescriptorFactory.getInstance(project).builder(vFile).offset(textOffset).build();
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiClassType
      type = JavaPsiFacade.getInstance(project).getElementFactory().createTypeByFQClassName("Object", GlobalSearchScope.allScope(project));
    GrVariableDeclaration decl = GroovyPsiElementFactory.getInstance(project)
                                                        .createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY,
                                                                                   "",
                                                                                   type,
                                                                                   myRefExpression.getReferenceName());
    int offset = myRefExpression.getTextRange().getStartOffset();
    GrStatement anchor = findAnchor(file, offset);

    TypeConstraint[] constraints = GroovyExpectedTypesProvider.calculateTypeConstraints(myRefExpression);
    if (myRefExpression.equals(anchor)) {
      decl = myRefExpression.replaceWithStatement(decl);
    }
    else {
      decl = myOwner.addVariableDeclarationBefore(decl, anchor);
    }
    GrTypeElement typeElement = decl.getTypeElementGroovy();
    assert typeElement != null;
    ChooseTypeExpression expr = new ChooseTypeExpression(constraints, PsiManager.getInstance(project), typeElement.getResolveScope());
    TemplateBuilderImpl builder = new TemplateBuilderImpl(decl);
    builder.replaceElement(typeElement, expr);
    decl = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(decl);
    Template template = builder.buildTemplate();

    Editor newEditor = positionCursor(project, myOwner.getContainingFile(), decl);
    TextRange range = decl.getTextRange();
    newEditor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    TemplateManager manager = TemplateManager.getInstance(project);
    manager.startTemplate(newEditor, template);
  }

  @Nullable
  private GrStatement findAnchor(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null && offset > 0) element = file.findElementAt(offset - 1);
    while (element != null) {
      if (myOwner.equals(element.getParent())) return element instanceof GrStatement ? (GrStatement)element : null;
      element = element.getParent();
    }
    return null;
  }


  public boolean startInWriteAction() {
    return true;
  }
}
