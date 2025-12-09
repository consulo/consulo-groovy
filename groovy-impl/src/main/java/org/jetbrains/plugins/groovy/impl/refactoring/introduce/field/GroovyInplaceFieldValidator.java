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

import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.findUsage.DescriptiveNameUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.impl.refactoring.introduce.GrIntroduceValidatorEngine;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import static consulo.language.editor.refactoring.util.CommonRefactoringUtil.htmlEmphasize;

/**
 * @author Max Medvedev
 */
public class GroovyInplaceFieldValidator extends GrIntroduceValidatorEngine {
    public GroovyInplaceFieldValidator(GrIntroduceContext context) {
        super(context, (toCheck, conflicts, varName) -> {
            if (toCheck instanceof GrVariable variable && varName.equals(variable.getName())) {
                conflicts.putValue(toCheck, GroovyRefactoringLocalize.field0IsAlreadyDefined(htmlEmphasize(varName)));
            }
            if (toCheck instanceof GrMethod method
                && GroovyPropertyUtils.isSimplePropertyAccessor(method)
                && varName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(method.getName()))) {
                conflicts.putValue(
                    toCheck,
                    GroovyRefactoringLocalize.accessToCreatedField0WillBeOverridenByMethod1(
                        htmlEmphasize(varName),
                        htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(toCheck))
                    )
                );
            }
        });
    }
}
