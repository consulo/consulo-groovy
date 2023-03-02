package org.jetbrains.plugins.groovy.impl.ivy;

import com.intellij.java.impl.codeInsight.AttachSourcesProvider;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.ProgressStreamUtil;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.Library;
import consulo.http.HttpProxyManager;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.Component;
import consulo.ui.event.UIEvent;
import consulo.util.concurrent.AsyncResult;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public abstract class AbstractAttachSourceProvider implements AttachSourcesProvider {

  private static final Logger LOG = Logger.getInstance(AbstractAttachSourceProvider.class);

  @Nullable
  protected static VirtualFile getJarByPsiFile(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return null;

    VirtualFile jar = ArchiveVfsUtil.getVirtualFileForJar(psiFile.getVirtualFile());

    if (jar == null || !jar.getName().endsWith(".jar")) return null;

    return jar;
  }

  @Nullable
  protected static Library getLibraryFromOrderEntriesList(List<LibraryOrderEntry> orderEntries) {
    if (orderEntries.isEmpty()) return null;

    Library library = orderEntries.get(0).getLibrary();
    if (library == null) return null;

    for (int i = 1; i < orderEntries.size(); i++) {
      if (!library.equals(orderEntries.get(i).getLibrary())) {
        return null;
      }
    }

    return library;
  }

  protected void addSourceFile(@Nullable VirtualFile jarRoot, @Nonnull Library library) {
    if (jarRoot != null) {
      if (!Arrays.asList(library.getFiles(SourcesOrderRootType.getInstance())).contains(jarRoot)) {
        Library.ModifiableModel model = library.getModifiableModel();
        model.addRoot(jarRoot, SourcesOrderRootType.getInstance());
        model.commit();
      }
    }
  }

  protected class AttachExistingSourceAction implements AttachSourcesAction {
    private final String myName;
    private final VirtualFile mySrcFile;
    private final Library myLibrary;

    public AttachExistingSourceAction(VirtualFile srcFile, Library library, String actionName) {
      mySrcFile = srcFile;
      myLibrary = library;
      myName = actionName;
    }

    @Override
    public String getName() {
      return myName;
    }

    @Override
    public String getBusyText() {
      return getName();
    }

    @Override
    public AsyncResult<Void> perform(@Nonnull List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull UIEvent<Component> uiEvent) {
      ApplicationManager.getApplication().assertIsDispatchThread();

      if (!mySrcFile.isValid()) {
        return AsyncResult.rejected();
      }

      if (myLibrary != getLibraryFromOrderEntriesList(orderEntriesContainingFile)) return AsyncResult.rejected();

      AsyncResult<Void> result = AsyncResult.undefined();
      AccessToken accessToken = WriteAction.start();
      try {
        addSourceFile(mySrcFile, myLibrary);
      }
      finally {
        accessToken.finish();
      }

      result.setDone();
      return result;
    }
  }

  protected abstract class DownloadSourcesAction implements AttachSourcesAction {
    protected final Project myProject;
    protected final String myUrl;
    protected final NotificationGroup myMessageGroupId;

    public DownloadSourcesAction(Project project, NotificationGroup messageGroupId, String url) {
      myProject = project;
      myUrl = url;
      myMessageGroupId = messageGroupId;
    }

    @Override
    public String getName() {
      return "Download Sources";
    }

    @Override
    public String getBusyText() {
      return "Downloading Sources...";
    }

    protected abstract void storeFile(byte[] content);

    @Override
    public AsyncResult<Void> perform(@Nonnull List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull UIEvent<Component> e) {
      final AsyncResult<Void> callback = AsyncResult.undefined();

      Task task = new Task.Backgroundable(myProject, "Downloading sources...", true) {
        @Override
        public void run(@Nonnull ProgressIndicator indicator) {
          final ByteArrayOutputStream out;

          try {
            LOG.info("Downloading sources jar: " + myUrl);

            indicator.checkCanceled();

            HttpURLConnection urlConnection = HttpProxyManager.getInstance().openHttpConnection(myUrl);

            int contentLength = urlConnection.getContentLength();

            out = new ByteArrayOutputStream(contentLength > 0 ? contentLength : 100 * 1024);

            try (InputStream in = urlConnection.getInputStream()) {
              ProgressStreamUtil.copyStreamContent(indicator, in, out, contentLength);
            }
          }
          catch (IOException e) {
            LOG.warn(e);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                new Notification(myMessageGroupId,
                                 "Downloading failed",
                                 "Failed to download sources: " + myUrl,
                                 NotificationType.ERROR)
                  .notify((Project)getProject());

                callback.setDone();
              }
            });
            return;
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              AccessToken accessToken = WriteAction.start();
              try {
                storeFile(out.toByteArray());
              }
              finally {
                accessToken.finish();
                callback.setDone();
              }
            }
          });
        }

        @Override
        public void onCancel() {
          callback.setRejected();
        }
      };

      task.queue();

      return callback;
    }
  }
}
