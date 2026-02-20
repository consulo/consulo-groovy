/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.util;

import consulo.component.persist.PersistentStateComponent;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public abstract class SdkHomeSettings implements PersistentStateComponent<SdkHomeConfigurable.SdkHomeBean> {
  private final PsiModificationTracker myTracker;
  private SdkHomeConfigurable.SdkHomeBean mySdkPath;

  protected SdkHomeSettings(Project project) {
    myTracker = PsiManager.getInstance(project).getModificationTracker();
  }

  public SdkHomeConfigurable.SdkHomeBean getState() {
    return mySdkPath;
  }

  public void loadState(SdkHomeConfigurable.SdkHomeBean state) {
    SdkHomeConfigurable.SdkHomeBean oldState = mySdkPath;
    mySdkPath = state;
    if (oldState != null) {
      myTracker.incCounter();
    }
  }

  @Nullable
  private static VirtualFile calcHome(SdkHomeConfigurable.SdkHomeBean state) {
    if (state == null) {
      return null;
    }

    @SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext"}) String sdk_home = state.SDK_HOME;
    if (StringUtil.isEmpty(sdk_home)) {
      return null;
    }

    return LocalFileSystem.getInstance().findFileByPath(sdk_home);
  }

  @Nullable
  public VirtualFile getSdkHome() {
    return calcHome(mySdkPath);
  }

  public List<VirtualFile> getClassRoots() {
    return calcRoots(getSdkHome());
  }

  private static List<VirtualFile> calcRoots(@Nullable VirtualFile home) {
    if (home == null) {
      return Collections.emptyList();
    }

    VirtualFile lib = home.findChild("lib");
    if (lib == null) {
      return Collections.emptyList();
    }

    ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (VirtualFile file : lib.getChildren()) {
      if ("jar".equals(file.getExtension())) {
        ContainerUtil.addIfNotNull(result, ArchiveVfsUtil.getJarRootForLocalFile(file));
      }
    }
    return result;
  }
}
