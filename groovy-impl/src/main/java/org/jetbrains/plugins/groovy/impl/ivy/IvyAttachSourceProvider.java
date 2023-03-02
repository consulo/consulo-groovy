package org.jetbrains.plugins.groovy.impl.ivy;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.Library;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class IvyAttachSourceProvider extends AbstractAttachSourceProvider {
  public static final NotificationGroup GROOVY_IVY = NotificationGroup.balloonGroup("Groovy Ivy");

  private static final Logger LOG = Logger.getInstance(IvyAttachSourceProvider.class);

  @Nullable
  private static String extractUrl(PropertiesFile properties, String artifactName) {
    String prefix = "artifact:" + artifactName + "#source#jar#";

    for (IProperty property : properties.getProperties()) {
      String key = property.getUnescapedKey();
      if (key != null && key.startsWith(prefix) && key.endsWith(".location")) {
        return property.getUnescapedValue();
      }
    }

    return null;
  }

  @Nonnull
  @Override
  public Collection<AttachSourcesAction> getActions(List<LibraryOrderEntry> orderEntries, final PsiFile psiFile) {
    VirtualFile jar = getJarByPsiFile(psiFile);
    if (jar == null) return Collections.emptyList();

    VirtualFile jarsDir = jar.getParent();
    if (jarsDir == null || !jarsDir.getName().equals("jars")) return Collections.emptyList();

    String jarNameWithoutExt = jar.getNameWithoutExtension();

    final VirtualFile artifactDir = jarsDir.getParent();
    if (artifactDir == null) return Collections.emptyList();

    String artifactName = artifactDir.getName();

    if (!jarNameWithoutExt.startsWith(artifactName)
      || !jarNameWithoutExt.substring(artifactName.length()).startsWith("-")) {
      return Collections.emptyList();
    }

    String version = jarNameWithoutExt.substring(artifactName.length() + 1);

    VirtualFile propertiesFile = artifactDir.findChild("ivydata-" + version + ".properties");
    if (propertiesFile == null) return Collections.emptyList();

    final Library library = getLibraryFromOrderEntriesList(orderEntries);
    if (library == null) return Collections.emptyList();

    final String sourceFileName = artifactName + '-' + version + "-sources.jar";

    final VirtualFile sources = artifactDir.findChild("sources");
    if (sources != null) {
      VirtualFile srcFile = sources.findChild(sourceFileName);
      if (srcFile != null) {
        // File already downloaded.
        VirtualFile jarRoot = ArchiveVfsUtil.getJarRootForLocalFile(srcFile);
        if (jarRoot == null || ArrayUtil.contains(jarRoot, library.getFiles(SourcesOrderRootType.getInstance()))) {
          return Collections.emptyList(); // Sources already attached.
        }

        return Collections.<AttachSourcesAction>singleton(new AttachExistingSourceAction(jarRoot,
                                                                                         library,
                                                                                         "Attache sources from Ivy repository"));
      }
    }

    PsiFile propertiesFileFile = psiFile.getManager().findFile(propertiesFile);
    if (!(propertiesFileFile instanceof PropertiesFile)) return Collections.emptyList();

    final String url = extractUrl((PropertiesFile)propertiesFileFile, artifactName);
    if (StringUtil.isEmptyOrSpaces(url)) return Collections.emptyList();

    return Collections.<AttachSourcesAction>singleton(new DownloadSourcesAction(psiFile.getProject(), GROOVY_IVY, url) {
      @Override
      protected void storeFile(byte[] content) {
        try {
          VirtualFile existingSourcesFolder = sources;
          if (existingSourcesFolder == null) {
            existingSourcesFolder = artifactDir.createChildDirectory(this, "sources");
          }

          VirtualFile srcFile = existingSourcesFolder.createChildData(this, sourceFileName);
          srcFile.setBinaryContent(content);

          addSourceFile(ArchiveVfsUtil.getJarRootForLocalFile(srcFile), library);
        }
        catch (IOException e) {
          new Notification(myMessageGroupId,
                           "IO Error",
                           "Failed to save " + artifactDir.getPath() + "/sources/" + sourceFileName,
                           NotificationType.ERROR)
            .notify(myProject);
          LOG.warn(e);
        }
      }
    });
  }

}
