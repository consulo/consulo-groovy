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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce;

import com.intellij.java.language.impl.codeInsight.PsiClassListCellRenderer;
import com.intellij.java.language.psi.PsiClass;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorPopupHelper;
import consulo.language.editor.ui.PopupNavigationUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.ui.ex.popup.JBPopup;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * Created by Max Medvedev on 8/29/13
 */
public abstract class GrIntroduceFieldHandlerBase<Settings extends GrIntroduceSettings> extends GrIntroduceHandlerBase<Settings, PsiClass> {
  @Nonnull
  @Override
  protected PsiClass[] findPossibleScopes(GrExpression expression,
                                          GrVariable variable,
                                          StringPartInfo partInfo,
                                          Editor editor) {
    PsiElement place = getCurrentPlace(expression, variable, partInfo);
    PsiClass aClass = PsiUtil.getContextClass(place);
    if (aClass instanceof GroovyScriptClass) {
      return new PsiClass[]{aClass};
    }
    else {
      List<PsiClass> result = ContainerUtil.newArrayList(aClass);
      while (aClass != null) {
        aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class);
        ContainerUtil.addIfNotNull(result, aClass);
      }
      return result.toArray(new PsiClass[result.size()]);
    }
  }

  @Override
  protected void showScopeChooser(PsiClass[] scopes, final Consumer<PsiClass> callback, Editor editor) {
    PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
      @Override
      public boolean execute(@Nonnull PsiClass element) {
        callback.accept(element);
        return false;
      }
    };

    JBPopup popup =
      PopupNavigationUtil.getPsiElementPopup(scopes, new PsiClassListCellRenderer(), "Choose class to introduce field", processor);

    EditorPopupHelper.getInstance().showPopupInBestPositionFor(editor, popup);
  }

  @Nonnull
  @Override
  protected PsiElement[] findOccurrences(@Nonnull GrExpression expression, @Nonnull PsiElement scope) {
    if (scope instanceof GroovyScriptClass) {
      scope = scope.getContainingFile();
    }
    return super.findOccurrences(expression, scope);
  }
}
