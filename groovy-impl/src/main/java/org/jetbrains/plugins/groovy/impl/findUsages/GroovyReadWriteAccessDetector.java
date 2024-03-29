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

import com.intellij.java.language.psi.PsiExpression;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.highlight.ReadWriteAccessDetector;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl(order = "before java")
public class GroovyReadWriteAccessDetector extends ReadWriteAccessDetector{
  @Override
  public boolean isReadWriteAccessible(PsiElement element) {
    return element instanceof GrVariable;
  }

  @Override
  public boolean isDeclarationWriteAccess(PsiElement element) {
    if (element instanceof GrVariable && ((GrVariable)element).getInitializerGroovy() != null) {
      return true;
    }
    return false;
  }

  @Override
  public Access getReferenceAccess(PsiElement referencedElement, PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @Override
  public Access getExpressionAccess(PsiElement expression) {
    if (expression instanceof GrExpression) {
      GrExpression expr = (GrExpression)expression;
      boolean readAccess = PsiUtil.isAccessedForReading(expr);
      boolean writeAccess = PsiUtil.isAccessedForWriting(expr);
      if (!writeAccess && expr instanceof GrReferenceExpression) {
        //when searching usages of fields, should show all found setters as a "only write usage"
        PsiElement actualReferee = ((GrReferenceExpression)expr).resolve();
        if (actualReferee instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)actualReferee)) {
          writeAccess = true;
          readAccess = false;
        }
      }
      if (writeAccess && readAccess) return Access.ReadWrite;
      return writeAccess ? Access.Write : Access.Read;
    }
    else if (expression instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)expression;
      boolean readAccess = com.intellij.java.language.psi.util.PsiUtil.isAccessedForReading(expr);
      boolean writeAccess = com.intellij.java.language.psi.util.PsiUtil.isAccessedForWriting(expr);
      if (!writeAccess && expr instanceof PsiReferenceExpression) {
        //when searching usages of fields, should show all found setters as a "only write usage"
        PsiElement actualReferee = ((PsiReferenceExpression)expr).resolve();
        if (actualReferee instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertySetter((PsiMethod)actualReferee)) {
          writeAccess = true;
          readAccess = false;
        }
      }
      if (writeAccess && readAccess) return Access.ReadWrite;
      return writeAccess ? Access.Write : Access.Read;
    }
    else {
      return Access.Read;
    }
  }
}
