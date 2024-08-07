/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.style;

import com.intellij.java.language.psi.PsiType;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.annotator.GrHighlightUtil;
import org.jetbrains.plugins.groovy.impl.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class AddReturnTypeFix implements IntentionAction {
  @Nonnull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("add.return.type");
  }

  @Nonnull
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("add.return.type.to.method.declaration");
  }

  @Override
  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (editor == null) return false;
    int offset = editor.getCaretModel().getOffset();
    GrMethod method = findMethod(file, offset);
    if (method == null && offset > 0) method = findMethod(file, offset - 1);
    return method != null && !method.isConstructor();
  }

  @Nullable
  private static GrMethod findMethod(PsiFile file, final int offset) {
    final PsiElement at = file.findElementAt(offset);
    if (at == null) return null;

    if (at.getParent() instanceof GrReturnStatement) {
      final GrReturnStatement returnStatement = ((GrReturnStatement)at.getParent());
      final PsiElement word = returnStatement.getReturnWord();

      if (!word.getTextRange().contains(offset)) return null;

      final GroovyPsiElement returnOwner = PsiTreeUtil.getParentOfType(returnStatement, GrClosableBlock.class, GrMethod.class);
      if (returnOwner instanceof GrMethod) {
        final GrTypeElement returnTypeElement = ((GrMethod)returnOwner).getReturnTypeElementGroovy();
        if (returnTypeElement == null) {
          return (GrMethod)returnOwner;
        }
      }

      return null;
    }

    final GrMethod method = PsiTreeUtil.getParentOfType(at, GrMethod.class, false, GrTypeDefinition.class, GrClosableBlock.class);
    if (method != null && GrHighlightUtil.getMethodHeaderTextRange(method).contains(offset)) {
      if (method.getReturnTypeElementGroovy() == null) {
        return method;
      }
    }

    return null;
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final GrMethod method = findMethod(file, editor.getCaretModel().getOffset());
    if (method == null) return;

    PsiType type = method.getInferredReturnType();
    if (type == null) type = PsiType.getJavaLangObject(PsiManager.getInstance(project), file.getResolveScope());
    type = TypesUtil.unboxPrimitiveTypeWrapper(type);
    method.setReturnType(type);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
