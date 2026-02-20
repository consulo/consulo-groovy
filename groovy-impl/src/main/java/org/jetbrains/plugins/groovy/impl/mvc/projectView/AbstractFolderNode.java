package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.ui.ex.tree.PresentationData;
import consulo.module.content.ProjectFileIndex;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.icon.IconDescriptorUpdaters;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class AbstractFolderNode extends AbstractMvcPsiNodeDescriptor {
  @Nullable private final String myLocationMark;

  private final String myPresentableText;

  protected AbstractFolderNode(@Nonnull Module module,
							   @Nonnull PsiDirectory directory,
							   @Nonnull String presentableText,
							   @Nullable String locationMark,
							   ViewSettings viewSettings, int weight) {
    super(module, viewSettings, directory, weight);
    myLocationMark = locationMark;
    myPresentableText = presentableText;
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull PsiElement psiElement) {
    VirtualFile virtualFile = getVirtualFile();
    assert virtualFile != null;

    return "Folder: " + virtualFile.getPresentableName();
  }

  @Nonnull
  protected PsiDirectory getPsiDirectory() {
    return (PsiDirectory)extractPsiFromValue();
  }

  @Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    PsiDirectory directory = getPsiDirectory();
    if (!directory.isValid()) {
      return Collections.emptyList();
    }

    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();

    // scan folder's children
    for (PsiDirectory subDir : directory.getSubdirectories()) {
      children.add(createFolderNode(subDir));
    }

    for (PsiFile file : directory.getFiles()) {
      processNotDirectoryFile(children, file);
    }

    return children;
  }

  private AbstractFolderNode createFolderNode(PsiDirectory directory) {
    PsiDirectory realDirectory = directory;

    StringBuilder textBuilder = null;

    if (getSettings().isHideEmptyMiddlePackages()) {
      do {
        if (realDirectory.getFiles().length > 0) break;

        PsiDirectory[] subdirectories = realDirectory.getSubdirectories();
        if (subdirectories.length != 1) break;

        if (textBuilder == null) {
          textBuilder = new StringBuilder();
          textBuilder.append(realDirectory.getName());
        }

        realDirectory = subdirectories[0];

        textBuilder.append('.').append(realDirectory.getName());
      } while (true);
    }

    String presentableText = textBuilder == null ? directory.getName() : textBuilder.toString();

    return new AbstractFolderNode(getModule(), realDirectory, presentableText, myLocationMark, getSettings(), FOLDER) {
      @Override
      protected void processNotDirectoryFile(List<AbstractTreeNode> nodes, PsiFile file) {
        AbstractFolderNode.this.processNotDirectoryFile(nodes, file);
      }

      @Override
      protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
        return AbstractFolderNode.this.createClassNode(typeDefinition);
      }
    };
  }

  @Override
  protected void updateImpl(PresentationData data) {
    PsiDirectory psiDirectory = getPsiDirectory();

    data.setPresentableText(myPresentableText);
    data.setIcon(IconDescriptorUpdaters.getIcon(psiDirectory, 0));
  }

  @Override
  protected boolean containsImpl(@Nonnull VirtualFile file) {
    PsiElement psiElement = extractPsiFromValue();
    if (psiElement == null || !psiElement.isValid()) {
      return false;
    }

    VirtualFile valueFile = ((PsiDirectory)psiElement).getVirtualFile();
    if (!VfsUtil.isAncestor(valueFile, file, false)) {
      return false;
    }

    Project project = psiElement.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(valueFile);
    if (module == null) {
      return fileIndex.getModuleForFile(file) == null;
    }

    return ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
  }

  protected void processNotDirectoryFile(List<AbstractTreeNode> nodes, PsiFile file) {
    if (file instanceof GroovyFile) {
      GrTypeDefinition[] definitions = ((GroovyFile)file).getTypeDefinitions();
      if (definitions.length > 0) {
        for (GrTypeDefinition typeDefinition : definitions) {
          nodes.add(createClassNode(typeDefinition));
        }
        return;
      }
    }
    nodes.add(new FileNode(getModule(), file, myLocationMark, getSettings()));
  }

  protected AbstractTreeNode createClassNode(GrTypeDefinition typeDefinition) {
    assert getValue() != null;

    return new ClassNode(getModule(), typeDefinition, getSettings());
  }

}
