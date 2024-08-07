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
package org.jetbrains.plugins.groovy.impl.refactoring;

import com.intellij.java.impl.psi.statistics.JavaStatisticsManager;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.rename.NameSuggestionProvider;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class GroovyNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, @Nullable PsiElement nameSuggestionContext, Set<String> result) {
    if (nameSuggestionContext == null) nameSuggestionContext = element;
    if (element instanceof GrVariable && nameSuggestionContext instanceof GroovyPsiElement) {
      final PsiType type = ((GrVariable)element).getTypeGroovy();
      if (type != null) {
        final String[] names = GroovyNameSuggestionUtil
          .suggestVariableNameByType(type, new DefaultGroovyVariableNameValidator((GroovyPsiElement)nameSuggestionContext));
        result.addAll(Arrays.asList(names));
        return new SuggestedNameInfo(names) {
          @Override
          public void nameChosen(String name) {
            JavaStatisticsManager
              .incVariableNameUseCount(name, JavaCodeStyleManager.getInstance(element.getProject()).getVariableKind((GrVariable)element),
                                       ((GrVariable)element).getName(), type);
          }
        };
      }
    }
    return null;
  }
}
