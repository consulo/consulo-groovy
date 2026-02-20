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

package org.jetbrains.plugins.groovy.config;

import consulo.application.WriteAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {
  @NonNls
  private static final Pattern GROOVY_ALL_JAR_PATTERN = Pattern.compile("groovy-all-(.*)\\.jar");

  private static GroovyConfigUtils myGroovyConfigUtils;
  @NonNls
  public static final String GROOVY_JAR_PATTERN_NOVERSION = "groovy\\.jar";
  @NonNls
  public static final String GROOVY_JAR_PATTERN = "groovy-(\\d.*)\\.jar";
  public static final String NO_VERSION = "<no version>";
  public static final String GROOVY1_7 = "1.7";
  public static final String GROOVY1_8 = "1.8";
  public static final String GROOVY2_0 = "2.0";
  public static final String GROOVY2_1 = "2.1";
  public static final String GROOVY2_2 = "2.2";
  public static final String GROOVY2_2_2 = "2.2.2";
  public static final String GROOVY2_3 = "2.3";

  GroovyConfigUtils() {
  }

  public static GroovyConfigUtils getInstance() {
    if (myGroovyConfigUtils == null) {
      myGroovyConfigUtils = new GroovyConfigUtils() {
        {
          STARTER_SCRIPT_FILE_NAME = "groovy";
        }
      };
    }
    return myGroovyConfigUtils;
  }

  @Nonnull
  public static File[] getGroovyAllJars(@Nonnull String path) {
    return GroovyUtils.getFilesInDirectoryByPattern(path, GROOVY_ALL_JAR_PATTERN);
  }

  public static boolean matchesGroovyAll(@Nonnull String name) {
    return GROOVY_ALL_JAR_PATTERN.matcher(name).matches() && !name.contains("src") && !name.contains("doc");
  }

  @Nonnull
  public String getSDKVersion(@Nonnull String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN_NOVERSION, MANIFEST_PATH);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path + "/embeddable", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    }
    if (groovyJarVersion == null) {
      groovyJarVersion = getSDKJarVersion(path, GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    }
    return groovyJarVersion == null ? UNDEFINED_VERSION : groovyJarVersion;
  }

  public boolean isSDKLibrary(Library library) {
    if (library == null) {
      return false;
    }
    return LibrariesUtil.getGroovyLibraryHome(library.getFiles(BinariesOrderRootType.getInstance())) != null;
  }

  @Nullable
  public String getSDKVersion(@Nonnull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module,
                                                                              new CachedValueProvider<String>() {
                                                                                @Override
                                                                                public Result<String> compute() {
                                                                                  String path =
                                                                                    LibrariesUtil.getGroovyHomePath(module);
                                                                                  if (path == null) {
                                                                                    return Result.create(null,
																																																				 ProjectRootManager.getInstance(
                                                                                                           module.getProject()));
                                                                                  }
                                                                                  return Result.create(getSDKVersion(path),
                                                                                                       ProjectRootManager.getInstance(module
                                                                                                                                        .getProject()));
                                                                                }
                                                                              });
  }

  public boolean isVersionAtLeast(PsiElement psiElement, String version) {
    return isVersionAtLeast(psiElement, version, true);
  }

  public boolean isVersionAtLeast(PsiElement psiElement, String version, boolean unknownResult) {
    Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
    if (module == null) {
      return unknownResult;
    }
    String sdkVersion = getSDKVersion(module);
    if (sdkVersion == null) {
      return unknownResult;
    }
    return sdkVersion.compareTo(version) >= 0;
  }

  @Nonnull
  public String getSDKVersion(PsiElement psiElement) {
    Module module = ModuleUtilCore.findModuleForPsiElement(psiElement);
    if (module == null) {
      return NO_VERSION;
    }
    String s = getSDKVersion(module);
    return s != null ? s : NO_VERSION;
  }


  @Override
  public boolean isSDKHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      String path = file.getPath();
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0 ||
        GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN_NOVERSION).length > 0 ||
        GroovyUtils.getFilesInDirectoryByPattern(path + "/embeddable", GROOVY_ALL_JAR_PATTERN).length >
          0 ||
        GroovyUtils.getFilesInDirectoryByPattern(path, GROOVY_JAR_PATTERN).length > 0) {
        return true;
      }
    }
    return false;
  }

  public boolean tryToSetUpGroovyFacetOnTheFly(Module module) {
    Project project = module.getProject();
    Library[] libraries = getAllSDKLibraries(project);
    if (libraries.length > 0) {
      Library library = libraries[0];
      int result = Messages.showOkCancelDialog(GroovyBundle.message("groovy.like.library.found.text",
                                                                    module.getName(), library.getName(), getSDKLibVersion(library)),
                                               GroovyBundle.message("groovy.like" +
                                                                      ".library.found"),
                                               Messages.getQuestionIcon());
      if (result == 0) {
        WriteAction.run(() ->
                        {
                          ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
                          LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
                          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
                          model.commit();
                        });

        return true;
      }
    }
    return false;
  }

  @Nonnull
  public String getSDKLibVersion(Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyLibraryHome(library));
  }

  public Collection<String> getSDKVersions(Library[] libraries) {
    return ContainerUtil.map2List(libraries, library -> getSDKLibVersion(library));
  }
}
