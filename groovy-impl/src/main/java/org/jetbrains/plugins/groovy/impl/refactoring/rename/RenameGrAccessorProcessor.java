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

import com.intellij.java.impl.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.content.scope.SearchScope;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.usage.UsageInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.isSimplePropertyAccessor;

/**
 * @author Maxim.Medvedev
 */
public class RenameGrAccessorProcessor extends RenameJavaMethodProcessor {
  @Override
  public boolean canProcessElement(@Nonnull PsiElement element) {
    return element instanceof PsiMethod &&
           !(element instanceof GrAccessorMethod) &&
           isSimplePropertyAccessor((PsiMethod)element);
  }

  @Override
  public void renameElement(PsiElement psiElement,
                            String newName,
                            UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException
  {

  }

  @Override
  public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
    super.prepareRenaming(element, newName, allRenames, scope);
    final PsiField field = GroovyPropertyUtils.findFieldForAccessor((PsiMethod)element, false);
    if (field != null) {

    }
  }
}
