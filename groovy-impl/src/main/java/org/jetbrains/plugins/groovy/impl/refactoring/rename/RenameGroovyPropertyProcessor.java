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
package org.jetbrains.plugins.groovy.impl.refactoring.rename;

import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.RenamePsiElementProcessor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(id = "groovyPropertyRenameProcessor")
public class RenameGroovyPropertyProcessor extends RenamePsiElementProcessor {
  Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.refactoring.rename.RenameGroovyPropertyProcessor");

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    LOG.assertTrue(element instanceof PropertyForRename);
    final List<? extends PsiElement> elementsToRename = ((PropertyForRename)element).getElementsToRename();
    for (PsiElement psiElement : elementsToRename) {
      if (psiElement instanceof GrField) {
        allRenames.put(psiElement, newName);
      }
      else if (psiElement instanceof GrMethod) {
        if (GroovyPropertyUtils.isSimplePropertyGetter((PsiMethod)psiElement)) {
          allRenames.put(psiElement, RenamePropertyUtil.getGetterNameByOldName(newName, ((PsiMethod)psiElement).getName()));
        }
        else {
          allRenames.put(psiElement, GroovyPropertyUtils.getSetterName(newName));
        }
      }
    }
    allRenames.remove(element);
  }

  @Override
  public void renameElement(PsiElement element, String newName, UsageInfo[] usages, @Nullable RefactoringElementListener listener)
    throws IncorrectOperationException
  {

    //do nothing
  }

  @Override
  public boolean canProcessElement(@Nonnull PsiElement element) {
    return element instanceof PropertyForRename;
  }
}
