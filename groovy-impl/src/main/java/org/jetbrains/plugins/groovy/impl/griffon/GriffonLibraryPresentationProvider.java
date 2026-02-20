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
package org.jetbrains.plugins.groovy.impl.griffon;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.LibraryKind;
import consulo.content.library.ui.LibraryEditor;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;
import org.jetbrains.plugins.groovy.impl.config.GroovyLibraryPresentationProviderBase;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nik
 */
@ExtensionImpl
public class GriffonLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  public static final LibraryKind GRIFFON_KIND = LibraryKind.create("griffon");
  @NonNls
  private static final Pattern GRIFFON_JAR_FILE_PATTERN = Pattern.compile("griffon-rt-(\\d.*)\\.jar");

  public GriffonLibraryPresentationProvider() {
    super(GRIFFON_KIND);
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    String[] jars = new File(path + "/dist").list();
    if (jars != null) {
      for (String fileName : jars) {
        if (fileName.endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(new File(path + ("/dist/") + fileName)), BinariesOrderRootType.getInstance());
        }
      }
    }

    jars = new File(path + "/lib").list();
    if (jars != null) {
      for (String fileName : jars) {
        if (fileName.endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(new File(path + "/lib/" + fileName)), BinariesOrderRootType.getInstance());
        }
      }
    }
  }

  @Override
  public boolean isSDKHome(@Nonnull VirtualFile file) {
    VirtualFile dist = file.findChild("dist");
    if (dist == null) {
      return false;
    }

    return isGriffonSdk(dist.getChildren());
  }

  @Nonnull
  @Override
  public String getSDKVersion(String path) {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    for (VirtualFile virtualFile : file.findChild("dist").getChildren()) {
      String version = getGriffonCoreJarVersion(virtualFile);
      if (version != null) {
        return version;
      }
    }
    throw new AssertionError(path);
  }

  @Override
  public boolean managesLibrary(VirtualFile[] libraryFiles) {
    return isGriffonSdk(libraryFiles);
  }

  static boolean isGriffonCoreJar(VirtualFile file) {
    return GRIFFON_JAR_FILE_PATTERN.matcher(file.getName()).matches();
  }

  @Nls
  @Override
  public String getLibraryVersion(VirtualFile[] libraryFiles) {
    return getGriffonVersion(libraryFiles);
  }

  public static boolean isGriffonSdk(VirtualFile[] files) {
    for (VirtualFile file : files) {
      if (isGriffonCoreJar(file)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static String getGriffonVersion(@Nonnull Module module) {
    for (OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        VirtualFile[] files = orderEntry.getFiles(BinariesOrderRootType.getInstance());
        if (isGriffonSdk(files)) {
          return getGriffonVersion(files);
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getGriffonVersion(VirtualFile[] libraryFiles) {
    for (VirtualFile file : libraryFiles) {
      String version = getGriffonCoreJarVersion(file);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  @Nullable
  private static String getGriffonCoreJarVersion(VirtualFile file) {
    Matcher matcher = GRIFFON_JAR_FILE_PATTERN.matcher(file.getName());
    if (matcher.matches()) {
      return matcher.group(1);
    }
    return null;
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return JetgroovyIcons.Griffon.Griffon;
  }

  @Nls
  @Nonnull
  @Override
  public String getLibraryCategoryName() {
    return "Griffon";
  }
}
