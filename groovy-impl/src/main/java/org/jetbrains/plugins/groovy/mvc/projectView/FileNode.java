package org.jetbrains.plugins.groovy.mvc.projectView;

import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import consulo.awt.TargetAWT;
import consulo.ide.IconDescriptorUpdaters;

/**
 * @author peter
 */
public class FileNode extends AbstractMvcPsiNodeDescriptor {
  public FileNode(@Nonnull final Module module,
                  @Nonnull final PsiFile file,
                  @Nullable final String locationMark,
                  final ViewSettings viewSettings) {
    super(module, viewSettings, new NodeId(file, locationMark), FILE);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final NodeId nodeId,
                                           @Nonnull final PsiElement psiElement) {
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
    data.setIcon(TargetAWT.to(IconDescriptorUpdaters.getIcon(value, Iconable.ICON_FLAG_READ_STATUS)));
  }
}
