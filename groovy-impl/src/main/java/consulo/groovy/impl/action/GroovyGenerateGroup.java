/*
 * Copyright 2013-2025 consulo.io
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
package consulo.groovy.impl.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;
import org.jetbrains.plugins.groovy.impl.actions.generate.accessors.GroovyGenerateGetterAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.accessors.GroovyGenerateGetterSetterAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.accessors.GroovyGenerateSetterAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.constructors.GroovyGenerateConstructorAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.equals.GroovyGenerateEqualsAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.missing.GroovyGenerateMethodMissingAction;
import org.jetbrains.plugins.groovy.impl.actions.generate.missing.GroovyGeneratePropertyMissingAction;

/**
 * @author UNV
 * @since 2025-10-24
 */
@ActionImpl(
    id = "GroovyGenerateGroup1",
    children = {
        @ActionRef(type = GroovyGenerateConstructorAction.class),
        @ActionRef(type = GroovyGenerateGetterAction.class),
        @ActionRef(type = GroovyGenerateSetterAction.class),
        @ActionRef(type = GroovyGenerateGetterSetterAction.class),
        @ActionRef(type = GroovyGenerateEqualsAction.class),
        @ActionRef(type = GroovyGenerateMethodMissingAction.class),
        @ActionRef(type = GroovyGeneratePropertyMissingAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_GENERATE),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "JavaGenerateGroup1")
    )
)
public class GroovyGenerateGroup extends DefaultActionGroup implements DumbAware {
    public GroovyGenerateGroup() {
        super(LocalizeValue.empty(), false);
    }
}
