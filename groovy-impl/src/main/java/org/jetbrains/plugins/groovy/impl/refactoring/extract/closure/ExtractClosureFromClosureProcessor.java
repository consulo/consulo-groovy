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
package org.jetbrains.plugins.groovy.impl.refactoring.extract.closure;

import com.intellij.java.impl.refactoring.IntroduceParameterRefactoring;
import com.intellij.java.impl.refactoring.introduceParameter.ExternalUsageInfo;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ref.Ref;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.GrIntroduceClosureParameterProcessor;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.GrIntroduceParameterSettings;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.GroovyIntroduceParameterUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class ExtractClosureFromClosureProcessor extends ExtractClosureProcessorBase {
  public ExtractClosureFromClosureProcessor(@Nonnull GrIntroduceParameterSettings helper) {
    super(helper);
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    if (!myHelper.generateDelegate()) {
      for (GrStatement statement : myHelper.getStatements()) {
        GroovyIntroduceParameterUtil.detectAccessibilityConflicts(statement, usagesIn, conflicts,
                                                                  myHelper.replaceFieldsWithGetters() !=
                                                                  IntroduceParameterRefactoring.REPLACE_FIELDS_WITH_GETTERS_NONE,
                                                                  myProject);
      }
    }
    return showConflicts(conflicts, usagesIn);
  }


  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    GrIntroduceClosureParameterProcessor.processExternalUsages(usages, myHelper, generateClosure(myHelper));
    GrIntroduceClosureParameterProcessor.processClosure(usages, myHelper);

    GrStatementOwner declarationOwner = GroovyRefactoringUtil.getDeclarationOwner(myHelper.getStatements()[0]);
    ExtractUtil.replaceStatement(declarationOwner, myHelper);
  }

  @Nonnull
  @Override
  protected UsageInfo[] findUsages() {
    final GrVariable var = (GrVariable)myHelper.getToSearchFor();
    if (var != null) {
      final List<UsageInfo> result = new ArrayList<UsageInfo>();
      for (PsiReference ref : ReferencesSearch.search(var, GlobalSearchScope.allScope(myHelper.getProject()), true)) {
        final PsiElement element = ref.getElement();
        if (element.getLanguage() != GroovyFileType.GROOVY_LANGUAGE) {
          result.add(new OtherLanguageUsageInfo(ref));
          continue;
        }

        final GrCall call = GroovyRefactoringUtil.getCallExpressionByMethodReference(element);
        if (call == null) continue;

        result.add(new ExternalUsageInfo(element));
      }
      return result.toArray(new UsageInfo[result.size()]);
    }
    return UsageInfo.EMPTY_ARRAY;
  }
}

