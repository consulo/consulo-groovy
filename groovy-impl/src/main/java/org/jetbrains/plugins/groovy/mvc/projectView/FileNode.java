package org.jetbrains.plugins.groovy.mvc.projectView;

import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.ui.ex.tree.PresentationData;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.project.ui.view.tree.PsiFileNode;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.module.Module;
import consulo.component.util.Iconable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.icon.IconDescriptorUpdaters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;

/**
 * @author peter
 */
public class FileNode extends AbstractMvcPsiNodeDescriptor {
  public FileNode(@Nonnull final Module module,
                  @Nonnull final PsiFile file,
                  @Nullable final String locationMark,
                  final ViewSettings viewSettings) {
    super(module, viewSettings, file, FILE);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final PsiElement psiElement) {
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
  protected void updateImpl(final PresentationData data) {
    final PsiFile value = extractPsiFromValue();
    assert value != null;
    data.setPresentableText(value.getName());
    data.setIcon(IconDescriptorUpdaters.getIcon(value, Iconable.ICON_FLAG_READ_STATUS));
  }
}
