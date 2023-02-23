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
package org.jetbrains.plugins.groovy.intentions.conversions;

import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.refactoring.openapi.impl.RenameRefactoringImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author Maxim.Medvedev
 */
public class RenameFileWithClassIntention extends Intention implements Consumer<GrTypeDefinition> {

  private String myNewFileName = null;

  @Override
  protected void processIntention(@Nonnull PsiElement element,
                                  Project project,
                                  Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    new RenameRefactoringImpl(project, file, myNewFileName, true, true).run();
  }

  @Nonnull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("rename.file.to.0", myNewFileName);
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new ClassNameDiffersFromFileNamePredicate(this);
  }

  @Override
  public void accept(GrTypeDefinition def) {
    final String name = def.getName();
    final PsiFile file = def.getContainingFile();
    myNewFileName = name + "." + FileUtil.getExtension(file.getName());
  }
}
