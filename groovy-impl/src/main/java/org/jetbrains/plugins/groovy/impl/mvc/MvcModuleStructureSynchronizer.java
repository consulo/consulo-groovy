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

package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.dumb.DumbAwareRunnable;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.util.ModificationTracker;
import consulo.content.ContentIterator;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.event.ModuleRootAdapter;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.module.event.ModuleAdapter;
import consulo.module.event.ModuleListener;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author peter
 */
@Singleton
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
public class MvcModuleStructureSynchronizer {
  private final Set<Pair<Object, SyncAction>> myActions = new CopyOnWriteArraySet<>();
  private final Project myProject;

  private Set<VirtualFile> myPluginRoots = Collections.emptySet();

  private long myModificationCount = 0;

  private boolean myOutOfModuleDirectoryCreatedActionAdded;

  public static boolean ourGrailsTestFlag;

  private final ModificationTracker myModificationTracker = () -> myModificationCount;

  @Inject
  public MvcModuleStructureSynchronizer(Project project, StartupManager startupManager) {
    myProject = project;

    if (project.isDefault()) {
      return;
    }

    final MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(ModuleRootListener.class, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        queue(SyncAction.SyncLibrariesInPluginsModule, myProject);
        queue(SyncAction.UpgradeFramework, myProject);
        queue(SyncAction.CreateAppStructureIfNeeded, myProject);
        queue(SyncAction.UpdateProjectStructure, myProject);
        queue(SyncAction.EnsureRunConfigurationExists, myProject);
        myModificationCount++;

        updateProjectViewVisibility();
      }
    });

    startupManager.registerPostStartupActivity(uiAccess -> uiAccess.give(this::projectOpened));

    connection.subscribe(ModuleListener.class, new ModuleAdapter() {
      @Override
      public void moduleAdded(Project project, Module module) {
        @RequiredUIAccess Runnable task = () ->
        {
          queue(SyncAction.UpdateProjectStructure, module);
          queue(SyncAction.CreateAppStructureIfNeeded, module);
        };

        if (UIAccess.isUIThread()) {
          task.run();
        }
        else {
          Application.get().invokeLater(task);
        }
      }
    });

    connection.subscribe(BulkFileListener.class, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
      @Override
      public void fileCreated(final VirtualFileEvent event) {
        myModificationCount++;

        final VirtualFile file = event.getFile();
        final String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName) || isApplicationDirectoryName(fileName)) {
          queue(SyncAction.UpdateProjectStructure, file);
          queue(SyncAction.EnsureRunConfigurationExists, file);
        }
        else if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
        else {
          if (!myProject.isInitialized()) {
            return;
          }

          final Module module = ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);

          if (module == null) { // Maybe it is creation of a plugin in plugin directory.
            if (file.isDirectory()) {
              if (myPluginRoots.contains(file.getParent())) {
                queue(SyncAction.UpdateProjectStructure, myProject);
                return;
              }

              if (!myOutOfModuleDirectoryCreatedActionAdded) {
                queue(SyncAction.OutOfModuleDirectoryCreated, myProject);
                myOutOfModuleDirectoryCreatedActionAdded = true;
              }
            }
            return;
          }

          if (!MvcConsole.isUpdatingVfsByConsoleProcess(module)) {
            return;
          }

          final MvcFramework framework = MvcFramework.getInstance(module);
          if (framework == null) {
            return;
          }

          if (framework.isToReformatOnCreation(file) || file.isDirectory()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                if (!file.isValid()) {
                  return;
                }
                if (!framework.hasSupport(module)) {
                  return;
                }

                final List<VirtualFile> files = new ArrayList<VirtualFile>();

                if (file.isDirectory()) {
                  ModuleRootManager.getInstance(module).getFileIndex().iterateContentUnderDirectory(file, new ContentIterator() {
                    @Override
                    public boolean processFile(VirtualFile fileOrDir) {
                      if (!fileOrDir.isDirectory() && framework.isToReformatOnCreation(fileOrDir)) {
                        files.add(file);
                      }
                      return true;
                    }
                  });
                }
                else {
                  files.add(file);
                }

                PsiManager manager = PsiManager.getInstance(myProject);

                for (VirtualFile virtualFile : files) {
                  PsiFile psiFile = manager.findFile(virtualFile);
                  if (psiFile != null) {
                    new consulo.ide.impl.idea.codeInsight.actions.ReformatCodeProcessor(myProject, psiFile, null, false).run();
                  }
                }
              }
            }, module.getDisposed());
          }
        }
      }

      @Override
      public void fileDeleted(VirtualFileEvent event) {
        myModificationCount++;

        final VirtualFile file = event.getFile();
        if (isLibDirectory(file) || isLibDirectory(event.getParent())) {
          queue(SyncAction.UpdateProjectStructure, file);
        }
      }

      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final String fileName = event.getFileName();
        if (MvcModuleStructureUtil.APPLICATION_PROPERTIES.equals(fileName)) {
          queue(SyncAction.UpdateProjectStructure, event.getFile());
        }
      }

      @Override
      public void fileMoved(VirtualFileMoveEvent event) {
        myModificationCount++;
      }

      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
          myModificationCount++;
        }
      }
    }));
  }

  public ModificationTracker getFileAndRootsModificationTracker() {
    return myModificationTracker;
  }

  public static MvcModuleStructureSynchronizer getInstance(Project project) {
    return project.getComponent(MvcModuleStructureSynchronizer.class);
  }

  private static boolean isApplicationDirectoryName(String fileName) {
    for (MvcFramework framework : MvcFramework.EP_NAME.getExtensions()) {
      if (framework.getApplicationDirectoryName().equals(fileName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isLibDirectory(@Nullable final VirtualFile file) {
    return file != null && "lib".equals(file.getName());
  }

  private void projectOpened() {
    queue(SyncAction.UpdateProjectStructure, myProject);
    queue(SyncAction.EnsureRunConfigurationExists, myProject);
    queue(SyncAction.UpgradeFramework, myProject);
    queue(SyncAction.CreateAppStructureIfNeeded, myProject);
  }

  private void queue(SyncAction action, Object on) {
    if (myActions.isEmpty()) {
      if (myProject.isDisposed()) {
        return;
      }
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().invokeLater(() -> runActions(), Application.get().getNoneModalityState());
        }
      });
    }

    myActions.add(Pair.create(on, action));
  }

  @Nonnull
  private List<Module> determineModuleBySyncActionObject(Object o) {
    if (o instanceof Module) {
      return Collections.singletonList((Module)o);
    }
    if (o instanceof Project) {
      return Arrays.asList(ModuleManager.getInstance((Project)o).getModules());
    }
    if (o instanceof VirtualFile) {
      final VirtualFile file = (VirtualFile)o;
      if (file.isValid()) {
        final Module module = ModuleUtil.findModuleForFile(file, myProject);
        if (module == null) {
          return Collections.emptyList();
        }

        return Collections.singletonList(module);
      }
    }
    return Collections.emptyList();
  }

  @TestOnly
  public static void forceUpdateProject(Project project) {
    project.getComponent(MvcModuleStructureSynchronizer.class).runActions();
  }

  private void runActions() {
    try {
      if (myProject.isDisposed()) {
        return;
      }

      if (ApplicationManager.getApplication().isUnitTestMode() && !ourGrailsTestFlag) {
        return;
      }

      Pair<Object, SyncAction>[] actions = myActions.toArray(new Pair[myActions.size()]);
      //get module by object and kill duplicates

      final Set<Trinity<Module, SyncAction, MvcFramework>> rawActions = new LinkedHashSet<Trinity<Module, SyncAction, MvcFramework>>();

      for (final Pair<Object, SyncAction> pair : actions) {
        for (Module module : determineModuleBySyncActionObject(pair.first)) {
          if (!module.isDisposed()) {
            final MvcFramework framework = (pair.second == SyncAction.CreateAppStructureIfNeeded)
              ? MvcFramework.getInstanceBySdk(module)
              : MvcFramework.getInstance(module);

            if (framework != null && !framework.isAuxModule(module)) {
              rawActions.add(Trinity.create(module, pair.second, framework));
            }
          }
        }
      }

      boolean isProjectStructureUpdated = false;

      for (final Trinity<Module, SyncAction, MvcFramework> rawAction : rawActions) {
        final Module module = rawAction.first;
        if (module.isDisposed()) {
          continue;
        }

        if (rawAction.second == SyncAction.UpdateProjectStructure && rawAction.third.updatesWholeProject()) {
          if (isProjectStructureUpdated) {
            continue;
          }
          isProjectStructureUpdated = true;
        }

        rawAction.second.doAction(module, rawAction.third);
      }
    }
    finally {
      // if there were any actions added during performSyncAction, clear them too
      // all needed actions are already added to buffer and have thus been performed
      // otherwise you may get repetitive 'run create-app?' questions
      myActions.clear();
    }
  }

  private enum SyncAction {
    SyncLibrariesInPluginsModule {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.syncSdkAndLibrariesInPluginsModule(module);
      }
    },

    UpgradeFramework {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.upgradeFramework(module);
      }
    },

    CreateAppStructureIfNeeded {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.createApplicationIfNeeded(module);
      }
    },

    UpdateProjectStructure {
      @Override
      void doAction(final Module module, final MvcFramework framework) {
        framework.updateProjectStructure(module);
      }
    },

    EnsureRunConfigurationExists {
      @Override
      void doAction(Module module, MvcFramework framework) {
        framework.ensureRunConfigurationExists(module);
      }
    },

    OutOfModuleDirectoryCreated {
      @Override
      void doAction(Module module, MvcFramework framework) {
        final Project project = module.getProject();
        final MvcModuleStructureSynchronizer mvcModuleStructureSynchronizer = MvcModuleStructureSynchronizer.getInstance(project);

        if (mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded) {
          mvcModuleStructureSynchronizer.myOutOfModuleDirectoryCreatedActionAdded = false;

          Set<VirtualFile> roots = new HashSet<VirtualFile>();

          for (String rootPath : MvcWatchedRootProvider.getRootsToWatch(project)) {
            ContainerUtil.addIfNotNull(roots, LocalFileSystem.getInstance().findFileByPath(rootPath));
          }

          if (!roots.equals(mvcModuleStructureSynchronizer.myPluginRoots)) {
            mvcModuleStructureSynchronizer.myPluginRoots = roots;
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                mvcModuleStructureSynchronizer.queue(UpdateProjectStructure, project);
              }
            });
          }
        }
      }
    };

    abstract void doAction(Module module, MvcFramework framework);
  }

  private void updateProjectViewVisibility() {
//    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
//      @Override
//      public void run() {
//        ApplicationManager.getApplication().invokeLater(new Runnable() {
//          @Override
//          public void run() {
//            if (myProject.isDisposed()) {
//              return;
//            }
//
//            for (ToolWindowEP ep : ToolWindowEP.EP_NAME.getExtensions()) {
//              if (MvcToolWindowDescriptor.class.isAssignableFrom(ep.getFactoryClass())) {
//                MvcToolWindowDescriptor descriptor = (MvcToolWindowDescriptor)ep.getToolWindowFactory();
//                String id = descriptor.getToolWindowId();
//                boolean shouldShow = MvcModuleStructureUtil.hasModulesWithSupport(myProject, descriptor.getFramework());
//
//                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
//
//                ToolWindow toolWindow = toolWindowManager.getToolWindow(id);
//
//                if (shouldShow && toolWindow == null) {
//                  toolWindow = toolWindowManager.registerToolWindow(id, true, ToolWindowAnchor.LEFT, myProject, true);
//                  toolWindow.setIcon(descriptor.getFramework().getToolWindowIcon());
//                  descriptor.createToolWindowContent(myProject, toolWindow);
//                }
//                else if (!shouldShow && toolWindow != null) {
//                  toolWindowManager.unregisterToolWindow(id);
//                  Disposer.dispose(toolWindow.getContentManager());
//                }
//              }
//            }
//          }
//        });
//      }
//    });
  }

}
