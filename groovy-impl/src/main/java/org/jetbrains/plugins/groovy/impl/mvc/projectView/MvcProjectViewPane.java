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

package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataManager;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.IdeView;
import consulo.ide.impl.idea.ide.projectView.BaseProjectTreeBuilder;
import consulo.ide.impl.idea.ide.projectView.impl.*;
import consulo.ide.impl.idea.ide.util.DeleteHandler;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.ide.impl.idea.openapi.roots.ui.configuration.actions.ModuleDeleteProvider;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.LangDataKeys;
import consulo.language.editor.refactoring.ui.CopyPasteDelegator;
import consulo.language.editor.util.EditorHelper;
import consulo.language.psi.*;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.project.ui.view.ProjectViewAutoScrollFromSourceHandler;
import consulo.project.ui.view.SelectInTarget;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ex.DeleteProvider;
import consulo.ui.ex.TreeExpander;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.AutoScrollToSourceHandler;
import consulo.ui.ex.awt.IJSwingUtilities;
import consulo.ui.ex.awt.tree.AbstractTreeBuilder;
import consulo.ui.ex.awt.tree.AbstractTreeUpdater;
import consulo.ui.ex.awt.tree.DefaultTreeExpander;
import consulo.ui.ex.awt.tree.TreeUtil;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.mvc.MvcFramework;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Krasislchikov
 */
public class MvcProjectViewPane extends AbstractProjectViewPSIPane implements IdeView {
  private final CopyPasteDelegator myCopyPasteDelegator;
  private final JComponent myComponent;
  private final DeleteProvider myDeletePSIElementProvider;
  private final ModuleDeleteProvider myDeleteModuleProvider =
    new ModuleDeleteProvider();

  private final AutoScrollToSourceHandler myAutoScrollToSourceHandler;
  private final MyAutoScrollFromSourceHandler myAutoScrollFromSourceHandler;

  @NonNls
  private final String myId;
  private final MvcToolWindowDescriptor myDescriptor;

  private MvcProjectViewState myViewState;

  public MvcProjectViewPane(final Project project, MvcToolWindowDescriptor descriptor) {
    super(project);
    myDescriptor = descriptor;
    myId = descriptor.getToolWindowId();

    myViewState = descriptor.getProjectViewState(project);

    class TreeUpdater implements Runnable, PsiModificationTrackerListener {
      private volatile boolean myInQueue;

      @Override
      public void run() {
        if (getTree() != null && getTreeBuilder() != null) {
          updateFromRoot(true);
        }
        myInQueue = false;
      }

      @Override
      public void modificationCountChanged() {
        if (!myInQueue) {
          myInQueue = true;
          ApplicationManager.getApplication().invokeLater(this);
        }
      }
    }

    project.getMessageBus().connect(this).subscribe(PsiModificationTrackerListener.class, new TreeUpdater());

    myComponent = createComponent();
    DataManager.registerDataProvider(myComponent, this);

    myAutoScrollFromSourceHandler = new MyAutoScrollFromSourceHandler();
    myAutoScrollToSourceHandler = new AutoScrollToSourceHandler() {
      @Override
      protected boolean isAutoScrollMode() {
        return myViewState.autoScrollToSource;
      }

      @Override
      protected void setAutoScrollMode(boolean state) {
        myViewState.autoScrollToSource = state;
      }
    };

    myAutoScrollFromSourceHandler.install();
    myAutoScrollToSourceHandler.install(getTree());
    myAutoScrollToSourceHandler.onMouseClicked(getTree());

    myCopyPasteDelegator = new CopyPasteDelegator(project, myComponent) {
      @Nonnull
      @Override
      protected PsiElement[] getSelectedElements() {
        return MvcProjectViewPane.this.getSelectedPSIElements();
      }
    };
    myDeletePSIElementProvider = new DeleteHandler.DefaultDeleteProvider();
  }

  public void setup(ToolWindow toolWindow) {
    JPanel p = new JPanel(new BorderLayout());
    p.add(myComponent, BorderLayout.CENTER);

    ContentManager contentManager = toolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(p, null, false);
    content.setDisposer(this);
    content.setCloseable(false);

    content.setPreferredFocusableComponent(createComponent());
    contentManager.addContent(content);

    contentManager.setSelectedContent(content, true);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new HideEmptyMiddlePackagesAction());
    group.add(myAutoScrollToSourceHandler.createToggleAction());
    group.add(myAutoScrollFromSourceHandler.createToggleAction());

    toolWindow.setAdditionalGearActions(group);

    TreeExpander expander = new DefaultTreeExpander(myTree);
    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    AnAction collapseAction = actionsManager.createCollapseAllAction(expander, myTree);
    collapseAction.getTemplatePresentation().setIcon(AllIcons.General.CollapseAll);

    toolWindow.setTitleActions(new AnAction[]{
      new ScrollFromSourceAction(),
      collapseAction
    });
  }

  public LocalizeValue getTitle() {
    throw new UnsupportedOperationException();
  }

  public Image getIcon() {
    return myDescriptor.getFramework().getIcon();
  }

  @Nonnull
  public String getId() {
    return myId;
  }

  public int getWeight() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInitiallyVisible() {
    throw new UnsupportedOperationException();
  }

  public SelectInTarget createSelectInTarget() {
    throw new UnsupportedOperationException();
  }

  @Nonnull
  @Override
  public BaseProjectTreeBuilder createBuilder(final DefaultTreeModel treeModel) {
    return new ProjectTreeBuilder(myProject, myTree, treeModel, null, (ProjectAbstractTreeStructureBase)myTreeStructure) {
      protected AbstractTreeUpdater createUpdater() {
        return createTreeUpdater(this);
      }
    };
  }

  @Override
  public ProjectAbstractTreeStructureBase createStructure() {
    final Project project = myProject;
    final String id = getId();
    return new ProjectTreeStructure(project, id) {

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return myViewState.hideEmptyMiddlePackages;
      }

      protected AbstractTreeNode createRoot(Project project, ViewSettings settings) {
        return new MvcProjectNode(project, this, myDescriptor);
      }
    };
  }

  protected ProjectViewTree createTree(final DefaultTreeModel treeModel) {
    return new ProjectViewTree(myProject, treeModel) {
      public String toString() {
        return myDescriptor.getFramework().getDisplayName() + " " + super.toString();
      }

      public DefaultMutableTreeNode getSelectedNode() {
        return MvcProjectViewPane.this.getSelectedNode();
      }
    };
  }

  protected AbstractTreeUpdater createTreeUpdater(AbstractTreeBuilder treeBuilder) {
    return new AbstractTreeUpdater(treeBuilder);
  }

  @Override
  public Object getData(Key<?> dataId) {
    if (LangDataKeys.PSI_ELEMENT == dataId) {
      PsiElement[] elements = getSelectedPSIElements();
      return elements.length == 1 ? elements[0] : null;
    }
    if (LangDataKeys.PSI_ELEMENT_ARRAY == dataId) {
      return getSelectedPSIElements();
    }
    if (LangDataKeys.MODULE_CONTEXT == dataId) {
      Object element = getSelectedElement();
      if (element instanceof Module) {
        return element;
      }
      return null;
    }
    if (LangDataKeys.MODULE_CONTEXT_ARRAY == dataId) {
      List<Module> moduleList = ContainerUtil.findAll(getSelectedElements(), Module.class);
      if (!moduleList.isEmpty()) {
        return moduleList.toArray(new Module[moduleList.size()]);
      }
      return null;
    }
    if (IdeView.KEY == dataId) {
      return this;
    }
    if (LangDataKeys.HELP_ID == dataId) {
      return "reference.toolwindows." + myId.toLowerCase();
    }
    if (LangDataKeys.CUT_PROVIDER == dataId) {
      return myCopyPasteDelegator.getCutProvider();
    }
    if (LangDataKeys.COPY_PROVIDER == dataId) {
      return myCopyPasteDelegator.getCopyProvider();
    }
    if (LangDataKeys.PASTE_PROVIDER == dataId) {
      return myCopyPasteDelegator.getPasteProvider();
    }
    if (LangDataKeys.DELETE_ELEMENT_PROVIDER == dataId) {
      for (Object element : getSelectedElements()) {
        if (element instanceof Module) {
          return myDeleteModuleProvider;
        }
      }
      return myDeletePSIElementProvider;
    }
    return super.getData(dataId);
  }

  @Nullable
  public static MvcProjectViewPane getView(Project project, MvcFramework framework) {
    ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(MvcToolWindowDescriptor.getToolWindowId(framework));
    Content content = window == null ? null : window.getContentManager().getContent(0);
    return content == null ? null : (MvcProjectViewPane)content.getDisposer();
  }

  public void selectElement(PsiElement element) {
    PsiFileSystemItem psiFile;

    if (!(element instanceof PsiFileSystemItem)) {
      psiFile = element.getContainingFile();
    }
    else {
      psiFile = (PsiFileSystemItem)element;
    }

    if (psiFile == null) {
      return;
    }

    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return;
    }

    selectFile(virtualFile, false);

    boolean requestFocus = true;

    if (psiFile instanceof PsiFile) {
      Editor editor = EditorHelper.openInEditor(element);
      if (editor != null) {
        ToolWindowManager.getInstance(myProject).activateEditorComponent();
        requestFocus = false;
      }
    }

    if (requestFocus) {
      selectFile(virtualFile, true);
    }
  }

  public PsiDirectory[] getDirectories() {
    return getSelectedDirectories();
  }

  public PsiDirectory getOrChooseDirectory() {
    return DirectoryChooserUtil.getOrChooseDirectory(this);
  }

  public static boolean canSelectFile(@Nonnull Project project, @Nonnull MvcFramework framework, VirtualFile file) {
    return getSelectPath(project, framework, file) != null;
  }

  @Nullable
  private List<Object> getSelectPath(VirtualFile file) {
    return getSelectPath(myProject, myDescriptor.getFramework(), file);
  }

  @Nullable
  private static List<Object> getSelectPath(@Nonnull Project project, @Nonnull MvcFramework framework, VirtualFile file) {
    if (file == null) {
      return null;
    }

    Module module = ModuleUtil.findModuleForFile(file, project);
    if (module == null || !framework.hasSupport(module)) {
      return null;
    }
    List<Object> result = new ArrayList<Object>();

    MvcProjectViewPane view = getView(project, framework);
    if (view == null) {
      return null;
    }

    MvcProjectNode root = (MvcProjectNode)view.getTreeBuilder().getTreeStructure().getRootElement();
    result.add(root);

    for (AbstractTreeNode moduleNode : root.getChildren()) {
      if (moduleNode.getValue() == module) {
        result.add(moduleNode);

        AbstractTreeNode<?> cur = moduleNode;

        path:
        while (true) {
          for (AbstractTreeNode descriptor : cur.getChildren()) {
            if (descriptor instanceof AbstractFolderNode) {
              AbstractFolderNode folderNode = (AbstractFolderNode)descriptor;
              VirtualFile dir = folderNode.getVirtualFile();
              if (dir != null && VfsUtil.isAncestor(dir, file, false)) {
                cur = folderNode;
                result.add(folderNode);
                if (dir.equals(file)) {
                  return result;
                }
                continue path;
              }
            }
            if (descriptor instanceof AbstractMvcPsiNodeDescriptor) {
              if (file.equals(((AbstractMvcPsiNodeDescriptor)descriptor).getVirtualFile())) {
                result.add(descriptor);
                return result;
              }
            }
          }
          return null;
        }
      }
    }
    return null;
  }

  public boolean canSelectFile(VirtualFile file) {
    return getSelectPath(file) != null;
  }

  public void selectFile(VirtualFile file, boolean requestFocus) {
    List<Object> path = getSelectPath(file);
    if (path == null) {
      return;
    }

    Object value = ((AbstractTreeNode)path.get(path.size() - 1)).getValue();
    select(value, file, requestFocus);
  }

  public void scrollFromSource() {
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    //final FileEditor[] editors = fileEditorManager.getSelectedEditors();
    //for (FileEditor fileEditor : editors) {
    //  if (fileEditor instanceof TextEditor) {
    //    Editor editor = ((TextEditor)fileEditor).getEditor();
    //    selectElement();
    //    selectElementAtCaret(editor);
    //    return;
    //  }
    //}
    VirtualFile[] selectedFiles = fileEditorManager.getSelectedFiles();
    if (selectedFiles.length > 0) {
      selectFile(selectedFiles[0], false);
    }
  }

  private void selectElementAtCaretNotLosingFocus() {
    if (IJSwingUtilities.hasFocus(this.getComponentToFocus())) {
      return;
    }
    scrollFromSource();
  }

  private class ScrollFromSourceAction extends AnAction implements DumbAware {
    private ScrollFromSourceAction() {
      super("Scroll from Source", "Select the file open in the active editor", AllIcons.General.Locate);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      scrollFromSource();
    }
  }

  private class MyAutoScrollFromSourceHandler extends ProjectViewAutoScrollFromSourceHandler {
    protected MyAutoScrollFromSourceHandler() {
      super(MvcProjectViewPane.this.myProject, MvcProjectViewPane.this.myComponent, MvcProjectViewPane.this);
    }

    @Override
    protected boolean isAutoScrollEnabled() {
      return myViewState.autoScrollFromSource;
    }

    @Override
    protected void setAutoScrollEnabled(boolean state) {
      myViewState.autoScrollFromSource = state;
      if (state) {
        selectElementAtCaretNotLosingFocus();
      }
    }

    @Override
    protected void selectElementFromEditor(@Nonnull FileEditor editor) {
      selectElementAtCaretNotLosingFocus();
    }
  }

  private class HideEmptyMiddlePackagesAction extends ToggleAction implements DumbAware {
    private HideEmptyMiddlePackagesAction() {
      super("Compact Empty Middle Packages", "Show/Compact Empty Middle Packages", AllIcons.ObjectBrowser.CompactEmptyPackages);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myViewState.hideEmptyMiddlePackages;
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      myViewState.hideEmptyMiddlePackages = flag;
      TreeUtil.collapseAll(myTree, 1);
    }
  }

}
