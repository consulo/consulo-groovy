/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.impl.find.findUsages.JavaFindUsagesHandler;
import com.intellij.java.impl.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.java.impl.ide.util.SuperMethodWarningUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.find.FindBundle;
import consulo.find.FindUsagesHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ContainerUtil;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyFieldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  @Inject
  public GroovyFieldFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@Nonnull PsiElement element) {
    return element instanceof GrField;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@Nonnull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, this) {
      @Nonnull
      @Override
      public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        final PsiField field = (PsiField)element;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiMethod[] getters = GroovyPropertyUtils.getAllGettersByField(field);
          PsiMethod[] setters = GroovyPropertyUtils.getAllSettersByField(field);
          if (getters.length + setters.length > 0) {
            final boolean doSearch;
            if (arePhysical(getters) || arePhysical(setters)) {
              if (ApplicationManager.getApplication().isUnitTestMode()) return PsiElement.EMPTY_ARRAY;
              doSearch = Messages.showYesNoDialog(FindBundle.message("find.field.accessors.prompt", field.getName()),
                                                  FindBundle.message("find.field.accessors.title"),
                                                  Messages.getQuestionIcon()) == DialogWrapper.OK_EXIT_CODE;
            }
            else {
              doSearch = true;
            }
            if (doSearch) {
              final List<PsiElement> elements = new ArrayList<PsiElement>();
              for (PsiMethod getter : getters) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(getter, FindBundle.message("find.super.method.warning.action.verb")));
              }

              for (PsiMethod setter : setters) {
                ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(setter, FindBundle.message("find.super.method.warning.action.verb")));
              }
              for (Iterator<PsiElement> iterator = elements.iterator(); iterator.hasNext(); ) {
                if (iterator.next() instanceof GrAccessorMethod) iterator.remove();
              }
              return PsiUtilCore.toPsiElementArray(elements);
            }
            else {
              return PsiElement.EMPTY_ARRAY;
            }
          }
        }
        return super.getSecondaryElements();
      }
    };
  }

  private static boolean arePhysical(PsiMethod[] methods) {
    for (PsiMethod method : methods) {
      if (method.isPhysical()) return true;
    }
    return false;
  }
}
