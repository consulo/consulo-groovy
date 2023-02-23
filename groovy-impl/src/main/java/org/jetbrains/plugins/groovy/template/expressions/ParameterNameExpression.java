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
package org.jetbrains.plugins.groovy.template.expressions;

import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.template.*;
import consulo.language.editor.template.TextResult;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import com.intellij.java.language.psi.codeStyle.VariableKind;
import consulo.language.psi.util.PsiTreeUtil;
import javax.annotation.Nullable;

import consulo.language.editor.template.ExpressionContext;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author ven
 */
public class ParameterNameExpression extends Expression {
  private final @Nullable String myDefaultName;

  public ParameterNameExpression(@Nullable String name) {
    myDefaultName = name;
  }

  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getEditor().getDocument());
    SuggestedNameInfo info = getNameInfo(context);
    if (info == null) return new TextResult("p");
    String[] names = info.names;
    if (names.length > 0) {
      return new TextResult(names[0]);
    }
    return null;
  }

  @Nullable
  private SuggestedNameInfo getNameInfo(ExpressionContext context) {
    Project project = context.getProject();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(context.getEditor().getDocument());
    assert file != null;
    PsiElement elementAt = file.findElementAt(context.getStartOffset());
    GrParameter parameter = PsiTreeUtil.getParentOfType(elementAt, GrParameter.class);
    if (parameter == null) return null;
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);
    return manager.suggestVariableName(VariableKind.PARAMETER, myDefaultName, null, parameter.getTypeGroovy());
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    SuggestedNameInfo info = getNameInfo(context);
    if (info == null) return null;
    LookupElement[] result = new LookupElement[info.names.length];
    int i = 0;
    for (String name : info.names) {
      result[i++] = LookupElementBuilder.create(name);
    }
    return result;
  }
}