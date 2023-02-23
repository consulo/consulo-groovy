package org.jetbrains.plugins.groovy.mvc;

import consulo.fileEditor.EditorNotificationBuilder;
import consulo.ide.impl.idea.ui.EditorNotificationPanel;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * @author sergey.evdokimov
 */
public class MvcConfigureNotification extends GroovyFrameworkConfigNotification {

  private final MvcFramework framework;

  public MvcConfigureNotification(MvcFramework framework) {
    this.framework = framework;
  }

  @Override
  public boolean hasFrameworkStructure(@Nonnull Module module) {
    VirtualFile appDir = framework.findAppDirectory(module);
    if (appDir == null) return false;

    return appDir.findChild("controllers") != null && appDir.findChild("conf") != null;
  }

  @Override
  public boolean hasFrameworkLibrary(@Nonnull Module module) {
    return framework.hasFrameworkJar(module);
  }

 /* public static void configure(@NotNull MvcFramework framework, @NotNull Module module) {
    final GroovyLibraryDescription description = framework.createLibraryDescription();
    final AddCustomLibraryDialog dialog = AddCustomLibraryDialog.createDialog(description, module, null);
    dialog.setTitle("Change " + framework.getDisplayName() + " SDK version");
    dialog.show();

    if (dialog.isOK()) {
      module.putUserData(MvcFramework.UPGRADE, Boolean.TRUE);
    }
  }    */

  @Nullable
  @Override
  public EditorNotificationBuilder createConfigureNotificationPanel(@Nonnull Module module, Supplier<EditorNotificationBuilder> factory) {
    EditorNotificationBuilder builder = factory.get();
    builder.withText(LocalizeValue.localizeTODO(framework.getFrameworkName() + " SDK is not configured for module '" + module.getName() + '\''));
    builder.withAction(LocalizeValue.localizeTODO("Configure " + framework.getFrameworkName() + " SDK"), uiEvent -> {
      // configure(framework, module);
    });
    return builder;
  }
}
