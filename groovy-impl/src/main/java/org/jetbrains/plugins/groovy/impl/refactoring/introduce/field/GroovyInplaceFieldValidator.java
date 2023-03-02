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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.field;

import static consulo.language.editor.refactoring.util.CommonRefactoringUtil.htmlEmphasize;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.ConflictReporter;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceValidatorEngine;
import consulo.language.findUsage.DescriptiveNameUtil;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiMethod;
import consulo.util.collection.MultiMap;

/**
 * @author Max Medvedev
 */
public class GroovyInplaceFieldValidator extends GrIntroduceValidatorEngine {
  public GroovyInplaceFieldValidator(GrIntroduceContext context) {
    super(context, new ConflictReporter() {
      @Override
      public void check(PsiElement toCheck, MultiMap<PsiElement, String> conflicts, String varName) {
        if (toCheck instanceof GrVariable && varName.equals(((GrVariable)toCheck).getName())) {
          conflicts.putValue(toCheck, GroovyRefactoringBundle.message("field.0.is.already.defined", htmlEmphasize(varName)));
        }
        if (toCheck instanceof GrMethod) {
          if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)toCheck) &&
              varName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(((PsiMethod)toCheck).getName()))) {
            conflicts.putValue(toCheck, GroovyRefactoringBundle.message("access.to.created.field.0.will.be.overriden.by.method.1", htmlEmphasize(varName), htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(toCheck))));
          }
        }
      }
    });
  }
}
