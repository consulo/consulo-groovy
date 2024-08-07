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

import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.content.library.ProjectLibraryTable;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class AbstractConfigUtils {

  // SDK-dependent entities
  @NonNls protected String STARTER_SCRIPT_FILE_NAME;

  private final Condition<Library> LIB_SEARCH_CONDITION = library -> isSDKLibrary(library);

  // Common entities
  @NonNls public static final String UNDEFINED_VERSION = "undefined";
  @NonNls public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";


  /**
   * Define, whether  given home is appropriate SDK home
   *
   * @param file
   * @return
   */
  public abstract boolean isSDKHome(final VirtualFile file);

  @Nonnull
  public abstract String getSDKVersion(@Nonnull String path);

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath      directory containing jar file
   * @param jarRegex     filename pattern for jar file
   * @param manifestPath path to manifest file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  @Nullable
  public static String getSDKJarVersion(String jarPath, final String jarRegex, String manifestPath) {
    return getSDKJarVersion(jarPath, Pattern.compile(jarRegex), manifestPath);
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath      directory containing jar file
   * @param jarPattern     filename pattern for jar file
   * @param manifestPath path to manifest file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  @Nullable
  public static String getSDKJarVersion(String jarPath, final Pattern jarPattern, String manifestPath) {
    try {
      File[] jars = GroovyUtils.getFilesInDirectoryByPattern(jarPath, jarPattern);
      if (jars.length != 1) {
        return null;
      }
      JarFile jarFile = new JarFile(jars[0]);
      try {
        JarEntry jarEntry = jarFile.getJarEntry(manifestPath);
        if (jarEntry == null) {
          return null;
        }
        final InputStream inputStream = jarFile.getInputStream(jarEntry);
        Manifest manifest;
        try {
          manifest = new Manifest(inputStream);
        }
        finally {
          inputStream.close();
        }
        final String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        if (version != null) {
          return version;
        }

        final Matcher matcher = jarPattern.matcher(jars[0].getName());
        if (matcher.matches() && matcher.groupCount() == 1) {
          return matcher.group(1);
        }
        return null;
      }
      finally {
        jarFile.close();
      }
    }
    catch (Exception e) {
      return null;
    }
  }

  public Library[] getProjectSDKLibraries(Project project) {
    if (project == null || project.isDisposed()) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), LIB_SEARCH_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public Library[] getAllSDKLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalSDKLibraries(), getProjectSDKLibraries(project));
  }

  public Library[] getAllUsedSDKLibraries(Project project) {
    final List<Library> libraries = new ArrayList<Library>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      libraries.addAll(Arrays.asList(getSDKLibrariesByModule(module)));
    }
    return libraries.toArray(new Library[libraries.size()]);
  }

  public Library[] getGlobalSDKLibraries() {
    return LibrariesUtil.getGlobalLibraries(LIB_SEARCH_CONDITION);
  }

  public abstract boolean isSDKLibrary(Library library);

  public Library[] getSDKLibrariesByModule(final Module module) {
    final Condition<Library> condition = new Condition<Library>() {
      public boolean value(Library library) {
        return isSDKLibrary(library);
      }
    };
    return LibrariesUtil.getLibrariesByCondition(module, condition);
  }


}
