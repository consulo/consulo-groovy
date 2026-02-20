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
package org.jetbrains.plugins.groovy.impl.config;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.LibraryKind;
import consulo.content.library.ui.LibraryEditor;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import jakarta.annotation.Nonnull;
import java.io.File;

/**
 * @author nik
 */
@ExtensionImpl
public class GroovyLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  public static final LibraryKind GROOVY_KIND = LibraryKind.create("groovy");

  public GroovyLibraryPresentationProvider() {
    super(GROOVY_KIND);
  }

  public boolean managesLibrary(VirtualFile[] libraryFiles) {
    return LibrariesUtil.getGroovyLibraryHome(libraryFiles) != null;
  }

  @Nls
  public String getLibraryVersion(VirtualFile[] libraryFiles) {
    String home = LibrariesUtil.getGroovyLibraryHome(libraryFiles);
    if (home == null) return AbstractConfigUtils.UNDEFINED_VERSION;

    return GroovyConfigUtils.getInstance().getSDKVersion(home);
  }

  @Nonnull
  public Image getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @Override
  public boolean isSDKHome(@Nonnull VirtualFile file) {
    return GroovyConfigUtils.getInstance().isSDKHome(file);
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File srcRoot = new File(path + "/src/main");
    if (srcRoot.exists()) {
      libraryEditor.addRoot(VirtualFileUtil.getUrlForLibraryRoot(srcRoot), SourcesOrderRootType.getInstance());
    }

    File[] jars;
    File libDir = new File(path + "/lib");
    if (libDir.exists()) {
      jars = libDir.listFiles();
    }
    else {
      jars = new File(path + "/embeddable").listFiles();
    }
    if (jars != null) {
      for (File file : jars) {
        if (file.getName().endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(file), BinariesOrderRootType.getInstance());
        }
      }
    }
  }

  @Nonnull
  @Override
  public String getSDKVersion(String path) {
    return GroovyConfigUtils.getInstance().getSDKVersion(path);
  }

  @Nls
  @Nonnull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy";
  }

}
