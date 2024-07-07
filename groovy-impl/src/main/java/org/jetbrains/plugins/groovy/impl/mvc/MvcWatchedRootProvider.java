/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.annotation.component.ExtensionImpl;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.content.WatchedRootsProvider;
import jakarta.inject.Inject;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
@ExtensionImpl
public class MvcWatchedRootProvider implements WatchedRootsProvider {
  private final Project myProject;

  @Inject
  public MvcWatchedRootProvider(Project project) {
    myProject = project;
  }

  @Nonnull
  public Set<String> getRootsToWatch() {
    return getRootsToWatch(myProject);
  }

  @Nonnull
  public static Set<String> getRootsToWatch(Project project) {
    if (!project.isInitialized()) return Collections.emptySet();

    Set<String> result = null;

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final MvcFramework framework = MvcFramework.getInstance(module);
      if (framework == null) continue;

      if (result == null) result = new HashSet<String>();

      File sdkWorkDir = framework.getCommonPluginsDir(module);
      if (sdkWorkDir != null) {
        result.add(sdkWorkDir.getAbsolutePath());
      }

      File globalPluginsDir = framework.getGlobalPluginsDir(module);
      if (globalPluginsDir != null) {
        result.add(globalPluginsDir.getAbsolutePath());
      }
    }

    return result == null ? Collections.<String>emptySet() : result;
  }

}
