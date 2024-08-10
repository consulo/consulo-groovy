package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2024-08-10
 */
@ExtensionImpl
public class DynamicManagerActivity implements PostStartupActivity, DumbAware {
    @Override
    public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
        DynamicManagerImpl impl = (DynamicManagerImpl) DynamicManager.getInstance(project);

        if (!impl.getRootElement().getContainingClasses().isEmpty()) {
            DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(project);

            // init woolwindow
            uiAccess.give(() -> wrapper.getToolWindow());
        }
    }
}
