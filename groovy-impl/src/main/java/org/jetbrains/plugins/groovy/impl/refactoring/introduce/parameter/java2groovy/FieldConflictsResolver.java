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

package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter.java2groovy;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 *         Date: Apr 29, 2009 2:20:17 PM
 */
public class FieldConflictsResolver {
  //  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.FieldConflictsResolver");
  private final GrCodeBlock myScope;
  private final PsiField myField;
  private final List<GrReferenceExpression> myReferenceExpressions;
  private PsiClass myQualifyingClass;

  public FieldConflictsResolver(String name, GrCodeBlock scope) {
    myScope = scope;
    if (myScope == null) {
      myField = null;
      myReferenceExpressions = null;
      return;
    }
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myScope.getProject());
    final PsiVariable oldVariable = facade.getResolveHelper().resolveAccessibleReferencedVariable(name, myScope);
    myField = oldVariable instanceof PsiField ? (PsiField)oldVariable : null;

    if (!(oldVariable instanceof PsiField)) {
      myReferenceExpressions = null;
      return;
    }
    myReferenceExpressions = new ArrayList<GrReferenceExpression>();
    for (PsiReference reference : ReferencesSearch.search(myField, new LocalSearchScope(myScope), false)) {
      final PsiElement element = reference.getElement();
      if (element instanceof GrReferenceExpression) {
        final GrReferenceExpression referenceExpression = (GrReferenceExpression)element;
        if (referenceExpression.getQualifierExpression() == null) {
          myReferenceExpressions.add(referenceExpression);
        }
      }
    }
    if (myField.hasModifierProperty(PsiModifier.STATIC)) {
      myQualifyingClass = myField.getContainingClass();
    }
  }

  public void fix() throws IncorrectOperationException {
    if (myField == null) return;
    final PsiManager manager = myScope.getManager();
    for (GrReferenceExpression referenceExpression : myReferenceExpressions) {
      if (!referenceExpression.isValid()) continue;
      final PsiElement newlyResolved = referenceExpression.resolve();
      if (!manager.areElementsEquivalent(newlyResolved, myField)) {
        qualifyReference(referenceExpression, myField, myQualifyingClass);
      }
    }
  }


  public static GrReferenceExpression qualifyReference(GrReferenceExpression referenceExpression,
                                                        final PsiMember member,
                                                        @Nullable final PsiClass qualifyingClass) throws IncorrectOperationException
  {
    PsiManager manager = referenceExpression.getManager();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(manager.getProject());

    GrReferenceExpression expressionFromText;
    if (qualifyingClass == null) {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        expressionFromText = (GrReferenceExpression)factory.createExpressionFromText(containingClass.getQualifiedName()+ ".this." + member.getName());
      }
      else {
        expressionFromText = (GrReferenceExpression)factory.createExpressionFromText("this." + member.getName());
      }
    }
    else {
      expressionFromText = (GrReferenceExpression)factory.createExpressionFromText(qualifyingClass.getQualifiedName()+ '.' + member.getName());
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    expressionFromText = (GrReferenceExpression)codeStyleManager.reformat(expressionFromText);
    return (GrReferenceExpression)referenceExpression.replace(expressionFromText);
  }

}
