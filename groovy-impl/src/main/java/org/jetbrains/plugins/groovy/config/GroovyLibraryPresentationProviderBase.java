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
package org.jetbrains.plugins.groovy.config;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.Nls;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.ui.image.Image;

/**
 * @author nik
 */
public abstract class GroovyLibraryPresentationProviderBase extends LibraryPresentationProvider<GroovyLibraryProperties> {
  public GroovyLibraryPresentationProviderBase(LibraryKind kind) {
    super(kind);
  }

  @Override
  public String getDescription(@Nonnull GroovyLibraryProperties properties) {
    final String version = properties.getVersion();
    return getLibraryCategoryName() + " library" + (version != null ? " of version " + version : ":");
  }

  @Override
  public GroovyLibraryProperties detect(@Nonnull List<VirtualFile> classesRoots) {
    final VirtualFile[] libraryFiles = VfsUtilCore.toVirtualFileArray(classesRoots);
    if (managesLibrary(libraryFiles)) {
      final String version = getLibraryVersion(libraryFiles);
      return new GroovyLibraryProperties(version);
    }
    return null;
  }

  protected abstract void fillLibrary(String path, LibraryEditor libraryEditor);

  public abstract boolean managesLibrary(final VirtualFile[] libraryFiles);

  @Nullable
  @Nls
  public abstract String getLibraryVersion(final VirtualFile[] libraryFiles);

  @Nonnull
  public abstract Image getIcon();

  public abstract boolean isSDKHome(@Nonnull VirtualFile file);

  public abstract @Nonnull
  String getSDKVersion(String path);

  @Nonnull
  @Nls public abstract String getLibraryCategoryName();

  @Nonnull
  @Nls
  public String getLibraryPrefix() {
    return StringUtil.toLowerCase(getLibraryCategoryName());
  }

  public boolean managesName(@Nonnull String name) {
    return StringUtil.startsWithIgnoreCase(name, getLibraryPrefix());
  }
}
