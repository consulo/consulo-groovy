/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.xml.psi.xml.XmlAttribute;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author aalmiray
 */
public class GriffonSourceInspector {
  public static List<GriffonSource> processModuleMetadata(final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, new CachedValueProvider<List<GriffonSource>>() {
      @Override
      public Result<List<GriffonSource>> compute() {
        List<GriffonSource> sources = new ArrayList<GriffonSource>();
        List<VirtualFile> dependencies = new ArrayList<VirtualFile>();
        ContainerUtil.addIfNotNull(dependencies, GriffonFramework.getInstance().getApplicationPropertiesFile(module));
        String applicationName = GriffonFramework.getInstance().getApplicationName(module);

        File sdkWorkDir = GriffonFramework.getInstance().getSdkWorkDir(module);
        // construct $griffonWorkDir/projects/$appName/plugins
        File pluginsDir = new File(sdkWorkDir, "/projects/" + applicationName + "/plugins/");
        if (pluginsDir.exists() && pluginsDir.canRead() && pluginsDir.isDirectory()) {
          //noinspection ConstantConditions
          for (File pluginDir : pluginsDir.listFiles()) {
            if (!pluginDir.isDirectory() || !pluginDir.canRead()) continue;
            File srcIdeSupportDir = new File(pluginDir, "src/ide-support");
            if (!srcIdeSupportDir.exists() || !srcIdeSupportDir.canRead()) continue;
            File ideaSupport = new File(srcIdeSupportDir, "idea.xml");
            VirtualFile ideaMetadata = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ideaSupport);
            if (ideaMetadata != null) {
              dependencies.add(ideaMetadata);
              PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(ideaMetadata);
              if (psiFile instanceof XmlFile) {
                XmlTag rootTag = ((XmlFile)psiFile).getRootTag();
                if (rootTag == null) continue;
                /*
                   Metadata file has the following format
                   <idea-griffon>
                     <source-root path="src/commons">
                       <navigation
                         description="Common Sources"
                         icon="groovy-icon"
                         weight="75" />
                     </source-root>
                   </idea-griffon>
                */

                for (XmlTag sourceRootTag : rootTag.findSubTags("source-root")) {
                  String path = sourceRootTag.getAttributeValue("path");
                  XmlTag navigationTag = sourceRootTag.findFirstSubTag("navigation");
                  String description = navigationTag == null ? "" : navigationTag.getAttributeValue("description");
                  XmlAttribute iconAttr = navigationTag == null ? null : navigationTag.getAttribute("icon");
                  XmlAttribute weightAttr = navigationTag == null ? null : navigationTag.getAttribute("weight");
                  String icon = iconAttr != null ? iconAttr.getValue() : "groovy-icon";
                  int weight = weightAttr != null ? Integer.parseInt(weightAttr.getValue()) : 75;

                  sources.add(new GriffonSource(path, new GriffonSource.Navigation(description, icon, weight)));
                }
              }
            }
          }
        }
        return Result.create(sources, dependencies);
      }
    });

  }

  public static class GriffonSource {
    private final String path;
    private final Navigation navigation;

    public GriffonSource(String path, Navigation navigation) {
      this.path = path;
      this.navigation = navigation;
    }

    public String getPath() {
      return path;
    }

    public Navigation getNavigation() {
      return navigation;
    }

    @Override
    public String toString() {
      return "GriffonSource{" + "path='" + path + '\'' + ", navigation=" + navigation + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GriffonSource that = (GriffonSource)o;

      if (!navigation.equals(that.navigation)) return false;
      if (!path.equals(that.path)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = path.hashCode();
      result = 31 * result + navigation.hashCode();
      return result;
    }

    public static class Navigation {
      private final String description;
      private final String icon;
      private final int weight;

      public Navigation(String description, String icon, int weight) {
        this.description = getNaturalName(description);
        this.icon = icon;
        this.weight = weight;
      }

      public String getDescription() {
        return description;
      }

      public Image getIcon() {
         // TODO [VISTALL] unsupported
         return Image.empty(Image.DEFAULT_ICON_SIZE);
//        String iconStr = icon.endsWith(".png") ? icon : icon + ".png";
//        if (iconStr.startsWith("/")) {
//          return IconLoader.getIcon(iconStr);
//        }
//        else {
//          return IconLoader.getIcon("/icons/griffon/" + iconStr);
//        }
      }

      public int getWeight() {
        return weight;
      }

      @Override
      public String toString() {
        return "Navigation{" + "description='" + description + '\'' + ", icon='" + icon + '\'' + ", weight=" + weight + '}';
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Navigation)) return false;

        Navigation that = (Navigation)o;

        if (weight != that.weight) return false;
        if (description != null ? !description.equals(that.description) : that.description != null) return false;
        if (icon != null ? !icon.equals(that.icon) : that.icon != null) return false;

        return true;
      }

      @Override
      public int hashCode() {
        int result = description != null ? description.hashCode() : 0;
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + weight;
        return result;
      }
    }
  }

  private static String getNaturalName(String name) {
    name = getShortName(name);
    List<String> words = new ArrayList<String>();
    int i = 0;
    char[] chars = name.toCharArray();
    for (int j = 0; j < chars.length; j++) {
      char c = chars[j];
      String w;
      if (i >= words.size()) {
        w = "";
        words.add(i, w);
      }
      else {
        w = words.get(i);
      }

      if (Character.isLowerCase(c) || Character.isDigit(c)) {
        if (Character.isLowerCase(c) && w.length() == 0) {
          c = Character.toUpperCase(c);
        }
        else if (w.length() > 1 && Character.isUpperCase(w.charAt(w.length() - 1))) {
          w = "";
          words.add(++i, w);
        }

        words.set(i, w + c);
      }
      else if (Character.isUpperCase(c)) {
        if ((i == 0 && w.length() == 0) || Character.isUpperCase(w.charAt(w.length() - 1))) {
          words.set(i, w + c);
        }
        else {
          words.add(++i, String.valueOf(c));
        }
      }
    }

    StringBuilder buf = new StringBuilder();
    for (Iterator<String> j = words.iterator(); j.hasNext(); ) {
      String word = j.next();
      buf.append(word);
      if (j.hasNext()) {
        buf.append(' ');
      }
    }
    return buf.toString();
  }

  private static String getShortName(String className) {
    return StringUtil.getShortName(className);
  }
}
