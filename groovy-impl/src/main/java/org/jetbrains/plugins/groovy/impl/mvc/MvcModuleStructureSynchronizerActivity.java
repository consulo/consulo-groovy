package org.jetbrains.plugins.groovy.impl.mvc;

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
public class MvcModuleStructureSynchronizerActivity implements PostStartupActivity, DumbAware {
    @Override
    public void runActivity(@Nonnull Project project, @Nonnull UIAccess uiAccess) {
        MvcModuleStructureSynchronizer synchronizer = project.getInstance(MvcModuleStructureSynchronizer.class);

        uiAccess.give(synchronizer::projectOpened);
    }
}
