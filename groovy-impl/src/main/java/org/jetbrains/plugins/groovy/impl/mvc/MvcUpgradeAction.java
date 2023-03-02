package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import consulo.module.Module;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * @author peter
 */
public class MvcUpgradeAction extends MvcActionBase {
  @Override
  protected void actionPerformed(@Nonnull AnActionEvent e, @Nonnull final Module module, @Nonnull final MvcFramework framework) {
   /*final GroovyLibraryDescription description = framework.createLibraryDescription();
    final AddCustomLibraryDialog dialog = AddCustomLibraryDialog.createDialog(description, module, new ParameterizedRunnable<ModifiableRootModel>() {
        @Override
        public void run(ModifiableRootModel modifiableRootModel) {
          removeOldMvcSdk(framework, modifiableRootModel);
        }
      });
    dialog.setTitle("Change " + framework.getDisplayName() + " SDK version");
    dialog.show();

    if (dialog.isOK()) {
      module.putUserData(MvcFramework.UPGRADE, Boolean.TRUE);
      module.putUserData(MvcModuleStructureUtil.LAST_MVC_VERSION, null);
    } */
  }

  private static void removeOldMvcSdk(MvcFramework framework, ModifiableRootModel model) {
    final LibraryPresentationManager presentationManager = LibraryPresentationManager.getInstance();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        final consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.LibrariesContainer container = consulo.ide.impl.idea.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory.createContainer(model);
        if (library != null) {
          final VirtualFile[] files = container.getLibraryFiles(library, BinariesOrderRootType.getInstance());
          if (presentationManager.isLibraryOfKind(Arrays.asList(files), framework.getLibraryKind())) {
            model.removeOrderEntry(entry);
          }
        }
      }
    }
  }

  @Override
  protected void updateView(AnActionEvent event, @Nonnull MvcFramework framework, @Nonnull Module module) {
    event.getPresentation().setIcon(framework.getIcon());
  }
}

