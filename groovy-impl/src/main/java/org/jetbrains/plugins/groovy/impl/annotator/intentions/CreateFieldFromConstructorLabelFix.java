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
package org.jetbrains.plugins.groovy.impl.annotator.intentions;

import com.intellij.java.language.psi.PsiType;
import consulo.groovy.localize.GroovyLocalize;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

/**
 * @author Maxim.Medvedev
 */
public class CreateFieldFromConstructorLabelFix extends GroovyFix {
    private final CreateFieldFix myFix;
    private final GrNamedArgument myNamedArgument;

    public CreateFieldFromConstructorLabelFix(GrTypeDefinition targetClass, GrNamedArgument namedArgument) {
        myFix = new CreateFieldFix(targetClass);
        myNamedArgument = namedArgument;
    }

    @Nullable
    private String getFieldName() {
        final GrArgumentLabel label = myNamedArgument.getLabel();
        assert label != null;
        return label.getName();
    }

    private TypeConstraint[] calculateTypeConstrains() {
        final GrExpression expression = myNamedArgument.getExpression();
        PsiType type = null;
        if (expression != null) {
            type = expression.getType();
        }
        if (type != null) {
            return new TypeConstraint[]{SupertypeConstraint.create(type, type)};
        }
        else {
            return TypeConstraint.EMPTY_ARRAY;
        }
    }

    @Nonnull
    @Override
    public String getName() {
        return GroovyLocalize.createFieldFromUsage(getFieldName()).get();
    }

    @Override
    @RequiredUIAccess
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
        myFix.doFix(project, ArrayUtil.EMPTY_STRING_ARRAY, getFieldName(), calculateTypeConstrains(), myNamedArgument);
    }
}
