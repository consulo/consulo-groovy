/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import com.intellij.java.language.psi.PsiType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class ParameterCastFix extends GroovyFix {
  private final GrExpression myArgument;
  private final PsiType myType;
  private final String myName;

  public ParameterCastFix(int param, @Nonnull PsiType type, @Nonnull GrExpression argument) {
    myArgument = argument;
    myType = PsiImplUtil.normalizeWildcardTypeByPosition(type, argument);

    StringBuilder builder = new StringBuilder();
    builder.append("Cast ");

    builder.append(param + 1);
    switch (param + 1) {
      case 1:
        builder.append("st");
        break;
      case 2:
        builder.append("nd");
        break;
      case 3:
        builder.append("rd");
        break;
      default:
        builder.append("th");
        break;
    }
    builder.append(" parameter to ").append(myType.getPresentableText());


    myName = builder.toString();
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
  {
    final PsiElement element = descriptor.getPsiElement();
    final GrArgumentList list = element instanceof GrArgumentList ? (GrArgumentList)element :PsiUtil.getArgumentsList(element);
    if (list == null) return;

    GrCastFix.doCast(project, myType, myArgument);
  }

  @Nonnull
  @Override
  public String getName() {
    return myName;
  }
}
