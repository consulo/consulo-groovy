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

package org.jetbrains.plugins.groovy.impl.refactoring.inline;

import com.intellij.java.impl.refactoring.HelpID;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.Language;
import consulo.language.editor.refactoring.inline.InlineHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GroovyInlineHandler implements InlineHandler {

  @Nullable
  public Settings prepareInlineElement(@Nonnull final PsiElement element, @Nullable Editor editor, boolean invokedOnReference) {
    if (element instanceof GrField) {
      return GrInlineFieldUtil.inlineFieldSettings((GrField)element, editor, invokedOnReference);
    }
    else if (element instanceof GrMethod) {
      return GroovyInlineMethodUtil.inlineMethodSettings((GrMethod)element, editor, invokedOnReference);
    }
    else {
      if (element instanceof GrTypeDefinition) {
        return null;      //todo inline to anonymous class, push members from super class
      }
    }

    if (element instanceof PsiMember) {
      String message = GroovyRefactoringBundle.message("cannot.inline.0.", getFullName(element));
      CommonRefactoringUtil.showErrorHint(element.getProject(), editor, message, "", HelpID.INLINE_FIELD);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }
    return null;
  }

  private static String getFullName(PsiElement psi) {
    final String name = DescriptiveNameUtil.getDescriptiveName(psi);
    return (UsageViewUtil.getType(psi) + " " + name).trim();
  }


  public void removeDefinition(PsiElement element, Settings settings) {
    final PsiElement owner = element.getParent().getParent();
    if (element instanceof GrVariable && owner instanceof GrVariableDeclarationOwner) {
      ((GrVariableDeclarationOwner)owner).removeVariable(((GrVariable)element));
    }
    if (element instanceof GrMethod) {
      element.delete();
    }
  }

  @Nullable
  public Inliner createInliner(PsiElement element, Settings settings) {
    if (element instanceof GrVariable) {
      return new GrVariableInliner((GrVariable)element, settings);
    }
    if (element instanceof GrMethod) {
      return new GroovyMethodInliner((GrMethod)element);
    }
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}

