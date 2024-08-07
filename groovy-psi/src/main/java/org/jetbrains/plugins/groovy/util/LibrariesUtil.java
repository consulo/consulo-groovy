/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.util;

import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.AccessRule;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleSourceOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import jakarta.annotation.Nonnull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * @author ilyas
 */
public class LibrariesUtil {
  public static final String SOME_GROOVY_CLASS = "org.codehaus.groovy.control.CompilationUnit";

  private LibrariesUtil() {
  }

  public static Library[] getLibrariesByCondition(final Module module, final Condition<Library> condition) {
    if (module == null) {
      return new Library[0];
    }
    final ArrayList<Library> libraries = new ArrayList<Library>();

    AccessRule.read(() -> populateOrderEntries(module, condition, libraries, false, new HashSet<>()));

    return libraries.toArray(new Library[libraries.size()]);
  }

  private static void populateOrderEntries(@Nonnull Module module,
                                           Condition<Library> condition,
                                           ArrayList<Library> libraries,
                                           boolean exportedOnly,
                                           Set<Module> visited) {
    if (!visited.add(module)) {
      return;
    }

    for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)entry;
        if (exportedOnly && !libEntry.isExported()) {
          continue;
        }

        Library library = libEntry.getLibrary();
        if (condition.value(library)) {
          libraries.add(library);
        }
      }
      else if (entry instanceof ModuleOrderEntry) {
        final Module dep = ((ModuleOrderEntry)entry).getModule();
        if (dep != null) {
          populateOrderEntries(dep, condition, libraries, true, visited);
        }
      }
    }
  }

  @Deprecated
  public static Library[] getGlobalLibraries(Condition<Library> condition) {
    return Library.EMPTY_ARRAY;
  }

  @Nonnull
  public static String getGroovyLibraryHome(Library library) {
    final VirtualFile[] classRoots = library.getFiles(BinariesOrderRootType.getInstance());
    final String home = getGroovyLibraryHome(classRoots);
    return home == null ? "" : home;
  }

  public static boolean hasGroovySdk(@Nullable Module module) {
    return module != null && getGroovyHomePath(module) != null;
  }

  @Nullable
  public static VirtualFile findJarWithClass(@Nonnull Module module, final String classQName) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    for (PsiClass psiClass : JavaPsiFacade.getInstance(module.getProject()).findClasses(classQName, scope)) {
      final VirtualFile local = ArchiveVfsUtil.getVirtualFileForJar(psiClass.getContainingFile().getVirtualFile());
      if (local != null) {
        return local;
      }
    }
    return null;
  }


  @Nullable
  public static String getGroovyHomePath(@Nonnull Module module) {
    final VirtualFile local = findJarWithClass(module, SOME_GROOVY_CLASS);
    if (local != null) {
      final VirtualFile parent = local.getParent();
      if (parent != null) {
        if (("lib".equals(parent.getName()) || "embeddable".equals(parent.getName())) && parent.getParent() != null) {
          return parent.getParent().getPath();
        }
        return parent.getPath();
      }
    }

    final String home = getGroovyLibraryHome(OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots());
    return StringUtil.isEmpty(home) ? null : home;
  }

  @Nullable
  private static String getGroovySdkHome(VirtualFile[] classRoots) {
    for (VirtualFile file : classRoots) {
      final String name = file.getName();
      if (name.matches(GroovyConfigUtils.GROOVY_JAR_PATTERN_NOVERSION) || name.matches(GroovyConfigUtils.GROOVY_JAR_PATTERN)) {
        String jarPath = file.getPresentableUrl();
        File realFile = new File(jarPath);
        if (realFile.exists()) {
          File parentFile = realFile.getParentFile();
          if (parentFile != null) {
            if ("lib".equals(parentFile.getName())) {
              return parentFile.getParent();
            }
            return parentFile.getPath();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getEmbeddableGroovyJar(VirtualFile[] classRoots) {
    for (VirtualFile file : classRoots) {
      final String name = file.getName();
      if (GroovyConfigUtils.matchesGroovyAll(name)) {
        String jarPath = file.getPresentableUrl();
        File realFile = new File(jarPath);
        if (realFile.exists()) {
          return realFile.getPath();
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getGroovyLibraryHome(VirtualFile[] classRoots) {
    final String sdkHome = getGroovySdkHome(classRoots);
    if (sdkHome != null) {
      return sdkHome;
    }

    final String embeddable = getEmbeddableGroovyJar(classRoots);
    if (embeddable != null) {
      final File emb = new File(embeddable);
      if (emb.exists()) {
        final File parent = emb.getParentFile();
        if ("embeddable".equals(parent.getName()) || "lib".equals(parent.getName())) {
          return parent.getParent();
        }
        return parent.getPath();
      }
    }
    return null;
  }

  @Nonnull
  public static VirtualFile getLocalFile(@Nonnull VirtualFile libFile) {
    final VirtualFileSystem system = libFile.getFileSystem();
    if (system instanceof ArchiveFileSystem) {
      final VirtualFile local = ArchiveVfsUtil.getVirtualFileForJar(libFile);
      if (local != null) {
        return local;
      }
    }
    return libFile;
  }

  public static void placeEntryToCorrectPlace(ModifiableRootModel model, LibraryOrderEntry addedEntry) {
    final OrderEntry[] order = model.getOrderEntries();
    //place library after module sources
    assert order[order.length - 1] == addedEntry;
    int insertionPoint = -1;
    for (int i = 0; i < order.length - 1; i++) {
      if (order[i] instanceof ModuleSourceOrderEntry) {
        insertionPoint = i + 1;
        break;
      }
    }
    if (insertionPoint >= 0) {
      for (int i = order.length - 1; i > insertionPoint; i--) {
        order[i] = order[i - 1];
      }
      order[insertionPoint] = addedEntry;
      model.rearrangeOrderEntries(order);
    }
  }

}
