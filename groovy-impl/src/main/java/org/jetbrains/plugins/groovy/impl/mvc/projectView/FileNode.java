package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.PsiFileNode;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.component.util.Iconable;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

/**
 * @author peter
 */
public class FileNode extends AbstractMvcPsiNodeDescriptor {
  public FileNode(@Nonnull Module module,
                  @Nonnull PsiFile file,
                  @Nullable String locationMark,
                  ViewSettings viewSettings) {
    super(module, viewSettings, file, FILE);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull PsiElement psiElement) {
    return "File: " + ((PsiFile)psiElement).getName();
  }

  @Override
  protected PsiFile extractPsiFromValue() {
    return (PsiFile)super.extractPsiFromValue();
  }

  protected Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }

  public Comparable getTypeSortKey() {
    String extension = PsiFileNode.extension(extractPsiFromValue());
    return extension == null ? null : new PsiFileNode.ExtensionSortKey(extension);
  }

  @Override
  protected void updateImpl(PresentationData data) {
    PsiFile value = extractPsiFromValue();
    assert value != null;
    data.setPresentableText(value.getName());
    data.setIcon(IconDescriptorUpdaters.getIcon(value, Iconable.ICON_FLAG_READ_STATUS));
  }
}
