package org.jetbrains.plugins.groovy.mvc;

import consulo.application.dumb.DumbAware;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.project.Project;
import consulo.project.startup.PostStartupActivity;
import consulo.ui.UIAccess;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectWithoutLibraryNotificator implements PostStartupActivity, DumbAware {
  @Override
  public void runActivity(Project project, UIAccess uiAccess) {
//    ReadAction.nonBlocking(() -> {
//      Pair<Module, MvcFramework> pair = findModuleWithoutLibrary(project);
//
//      if (pair != null) {
//        final MvcFramework framework = pair.second;
//        final Module module = pair.first;
//
//        new Notification(framework.getFrameworkName() + ".Configure",
//                         framework.getFrameworkName() + " SDK not found.",
//                         "<html><body>Module '" + module.getName() + "' has no " + framework.getFrameworkName() + " SDK. <a " +
//                           "href='create'>Configure SDK</a></body></html>",
//                         NotificationType.INFORMATION,
//                         new NotificationListener() {
//                           @Override
//                           public void hyperlinkUpdate(@Nonnull Notification notification, @Nonnull HyperlinkEvent event) {
//                             //MvcConfigureNotification.configure(framework, module);
//                           }
//                         }).notify(project);
//      }
//    }).inSmartMode(project).submit(AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private static Pair<Module, MvcFramework> findModuleWithoutLibrary(Project project) {
    List<MvcFramework> frameworks = MvcFramework.EP_NAME.getExtensionList();

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (MvcFramework framework : frameworks) {
        VirtualFile appRoot = framework.findAppRoot(module);
        if (appRoot != null && appRoot.findChild("application.properties") != null) {
          if (!framework.hasFrameworkJar(module)) {
            return Pair.create(module, framework);
          }
        }
      }
    }

    return null;
  }
}
