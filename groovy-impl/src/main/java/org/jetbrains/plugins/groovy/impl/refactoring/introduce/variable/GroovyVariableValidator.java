/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.variable;

import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceValidatorEngine;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import static consulo.language.editor.refactoring.util.CommonRefactoringUtil.htmlEmphasize;

/**
 * @author ilyas
 */
public class GroovyVariableValidator extends GrIntroduceValidatorEngine implements GrIntroduceVariableHandler.Validator {
    public GroovyVariableValidator(GrIntroduceContext context) {
        super(context, (element, conflicts, varName) -> {
            if (!(element instanceof GrVariable var && !(var instanceof GrField))) {
                return;
            }

            if (var instanceof GrParameter && varName.equals(var.getName())) {
                conflicts.putValue(
                    var,
                    GroovyRefactoringLocalize.introducedVariableConflictsWithParameter0(htmlEmphasize(varName))
                );
            }
            else if (varName.equals(var.getName())) {
                conflicts.putValue(
                    var,
                    GroovyRefactoringLocalize.introducedVariableConflictsWithVariable0(htmlEmphasize(varName))
                );
            }
        });
    }
}
