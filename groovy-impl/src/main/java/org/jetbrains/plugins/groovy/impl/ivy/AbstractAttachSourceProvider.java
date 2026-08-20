package org.jetbrains.plugins.groovy.impl.ivy;

import com.intellij.java.impl.codeInsight.AttachSourcesProvider;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.concurrent.coroutine.WriteLock;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.ProgressStreamUtil;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.library.Library;
import consulo.http.HttpProxyManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.Component;
import consulo.ui.event.ComponentEvent;
import consulo.util.concurrent.AsyncResult;
import consulo.util.concurrent.coroutine.CoroutineScope;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * @author Sergey Evdokimov
 */
public abstract class AbstractAttachSourceProvider implements AttachSourcesProvider {

    private static final Logger LOG = Logger.getInstance(AbstractAttachSourceProvider.class);

    @Nullable
    protected static VirtualFile getJarByPsiFile(PsiFile psiFile) {
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }

        VirtualFile jar = ArchiveVfsUtil.getVirtualFileForJar(psiFile.getVirtualFile());

        if (jar == null || !jar.getName().endsWith(".jar")) {
            return null;
        }

        return jar;
    }

    @Nullable
    protected static Library getLibraryFromOrderEntriesList(List<LibraryOrderEntry> orderEntries) {
        if (orderEntries.isEmpty()) {
            return null;
        }

        Library library = orderEntries.get(0).getLibrary();
        if (library == null) {
            return null;
        }

        for (int i = 1; i < orderEntries.size(); i++) {
            if (!library.equals(orderEntries.get(i).getLibrary())) {
                return null;
            }
        }

        return library;
    }

    protected void addSourceFile(@Nullable VirtualFile jarRoot, @Nonnull Library library) {
        if (jarRoot != null) {
            if (!Arrays.asList(library.getFiles(SourcesOrderRootType.ID)).contains(jarRoot)) {
                Library.ModifiableModel model = library.getModifiableModel();
                model.addRoot(jarRoot, SourcesOrderRootType.ID);
                model.commit();
            }
        }
    }

    protected class AttachExistingSourceAction implements AttachSourcesAction {
        private final LocalizeValue myName;
        private final Project myProject;
        private final VirtualFile mySrcFile;
        private final Library myLibrary;

        public AttachExistingSourceAction(Project project, VirtualFile srcFile, Library library, LocalizeValue actionName) {
            myProject = project;
            mySrcFile = srcFile;
            myLibrary = library;
            myName = actionName;
        }

        @Override
        public LocalizeValue getName() {
            return myName;
        }

        @Override
        public LocalizeValue getBusyText() {
            return getName();
        }

        @Override
        public CompletableFuture<?> perform(@Nonnull List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull ComponentEvent<Component> uiEvent) {
            if (!mySrcFile.isValid()) {
                return CompletableFuture.failedFuture(new CancellationException());
            }

            if (myLibrary != getLibraryFromOrderEntriesList(orderEntriesContainingFile)) {
                return CompletableFuture.failedFuture(new CancellationException());
            }

            return WriteLock.apply((o, continuation) -> {
                    addSourceFile(mySrcFile, myLibrary);
                    return null;
                }).toCoroutine().runAsync(CoroutineScope.of(myProject.coroutineContext()), null)
                .toFuture();
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
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Download Sources");
        }

        @Override
        public LocalizeValue getBusyText() {
            return LocalizeValue.localizeTODO("Downloading Sources...");
        }

        protected abstract void storeFile(byte[] content);

        @Override
        public CompletableFuture<?> perform(@Nonnull List<LibraryOrderEntry> orderEntriesContainingFile, @Nonnull ComponentEvent<Component> e) {
            final CompletableFuture<?> callback = new CompletableFuture<>();

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
                                    .notify((Project) getProject());

                                callback.completeExceptionally(e);
                            }
                        });
                        return;
                    }

                    WriteLock.apply((o, continuation) -> {
                            storeFile(out.toByteArray());
                            return null;
                        }).toCoroutine().runAsync(CoroutineScope.of(DownloadSourcesAction.this.myProject.coroutineContext()), null)
                        .toFuture()
                        .whenCompleteAsync((o, throwable) -> {
                            if (throwable != null) {
                                callback.completeExceptionally(throwable);
                            }
                            else {
                                callback.complete(null);
                            }
                        });
                }

                @Override
                public void onCancel() {
                    callback.completeExceptionally(new CancellationException());
                }
            };

            task.queue();

            return callback;
        }
    }
}
