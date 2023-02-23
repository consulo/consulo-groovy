/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gant;

import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.LibraryKind;
import consulo.content.library.ui.LibraryEditor;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * @author nik
 */
public class GantLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  private static final LibraryKind GANT_KIND = LibraryKind.create("gant");

  public GantLibraryPresentationProvider() {
    super(GANT_KIND);
  }

  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return GantUtils.isGantLibrary(libraryFiles);
  }

  @Nls
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    return GantUtils.getGantVersion(GantUtils.getGantLibraryHome(libraryFiles));
  }

  @Nonnull
  public Image getIcon() {
    return JetgroovyIcons.Groovy.Gant_sdk;
  }

  @Override
  public boolean isSDKHome(@Nonnull VirtualFile file) {
    return GantUtils.isGantSdkHome(file);
  }

  @Nonnull
  @Override
  public String getSDKVersion(String path) {
    return GantUtils.getGantVersion(path);
  }

  @Nls
  @Nonnull
  @Override
  public String getLibraryCategoryName() {
    return "Gant";
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File srcRoot = new File(path + "/src/main");
    if (srcRoot.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), SourcesOrderRootType.getInstance());
    }

    // Add Gant jars
    File lib = new File(path + "/lib");
    File[] jars = lib.exists() ? lib.listFiles() : new File[0];
    if (jars != null) {
      for (File file : jars) {
        if (file.getName().endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(file), BinariesOrderRootType.getInstance());
        }
      }
    }
  }
}
