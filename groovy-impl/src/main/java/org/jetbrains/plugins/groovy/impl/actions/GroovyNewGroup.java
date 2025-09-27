package org.jetbrains.plugins.groovy.impl.actions;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;

/**
 * @author UNV
 * @since 2025-09-27
 */
@ActionImpl(
    id = "Groovy.NewGroup",
    children = {
        @ActionRef(type = NewGroovyClassAction.class),
        @ActionRef(type = NewScriptAction.class)
    },
    parents = @ActionParentRef(
        value = @ActionRef(id = IdeActions.GROUP_NEW),
        anchor = ActionRefAnchor.AFTER,
        relatedToAction = @ActionRef(id = "NewGroup1")
    )
)
public class GroovyNewGroup extends DefaultActionGroup implements DumbAware {
    public GroovyNewGroup() {
        super(LocalizeValue.empty(), false);
    }
}
