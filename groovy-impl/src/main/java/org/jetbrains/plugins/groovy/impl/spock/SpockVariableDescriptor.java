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
package org.jetbrains.plugins.groovy.impl.spock;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.PsiVariable;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.util.RecursionManager;
import consulo.application.util.function.Computable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class SpockVariableDescriptor {

  private final String myName;

  private final PsiElement myNavigationElement;

  private List<GrExpression> myExpressions;
  private List<GrExpression> myExpressionsOfCollection;

  private PsiVariable myVariable;

  public SpockVariableDescriptor(PsiElement navigationElement, String name) {
    myName = name;
    myNavigationElement = navigationElement;
    myExpressions = new ArrayList<GrExpression>();
  }

  public SpockVariableDescriptor addExpression(@Nullable GrExpression expression) {
    myExpressions.add(expression);
    return this;
  }

  public SpockVariableDescriptor addExpressionOfCollection(@Nullable GrExpression expression) {
    if (myExpressionsOfCollection == null) {
      myExpressionsOfCollection = new ArrayList<GrExpression>();
    }

    myExpressionsOfCollection.add(expression);
    return this;
  }

  public String getName() {
    return myName;
  }

  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public PsiVariable getVariable() {
    if (myVariable == null) {
      final PsiManager manager = myNavigationElement.getManager();

      PsiType type = RecursionManager.doPreventingRecursion(this, true, new Computable<PsiType>() {
        @Override
        public PsiType compute() {
          PsiType res = null;

          for (GrExpression expression : myExpressions) {
            if (expression == null) continue;

            res = TypesUtil.getLeastUpperBoundNullable(res, expression.getType(), manager);
          }

          if (myExpressionsOfCollection != null) {
            for (GrExpression expression : myExpressionsOfCollection) {
              if (expression == null) continue;

              PsiType listType = expression.getType();
              PsiType type = PsiUtil.extractIterableTypeParameter(listType, true);

              if (type == null) {
                if (listType == null) continue;

                if (listType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
                  type = PsiType.getJavaLangString(expression.getManager(), expression.getResolveScope());
                }
              }

              res = TypesUtil.getLeastUpperBoundNullable(res, type, manager);
            }
          }

          return res;
        }
      });

      if (type == null) {
        type = PsiType.getJavaLangObject(manager, myNavigationElement.getResolveScope());
      }

      myVariable = new SpockVariable(manager, myName, type, myNavigationElement);
    }

    return myVariable;
  }

  private static class SpockVariable extends GrLightVariable {
    public SpockVariable(PsiManager manager,
                         @NonNls String name,
                         @Nonnull PsiType type,
                         @Nonnull PsiElement navigationElement) {
      super(manager, name, type, navigationElement);
    }



    @Override
    public boolean isEquivalentTo(PsiElement another) {
      return super.isEquivalentTo(another)
             || (another instanceof SpockVariable && getNavigationElement() == another.getNavigationElement());
    }
  }

  @Override
  public String toString() {
    return myName;
  }
}
