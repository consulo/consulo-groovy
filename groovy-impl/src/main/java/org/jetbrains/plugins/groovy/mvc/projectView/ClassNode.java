package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import javax.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class ClassNode extends AbstractMvcPsiNodeDescriptor {
  public ClassNode(@Nonnull final Module module,
                   @Nonnull final GrTypeDefinition rClass,
                   @javax.annotation.Nullable final String locationMark,
                   @javax.annotation.Nullable final ViewSettings viewSettings) {
    super(module, viewSettings, new NodeId(rClass, locationMark), CLASS);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final NodeId nodeId, @Nonnull final PsiElement psiElement) {
    return "GrTypeDefinition: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected GrTypeDefinition extractPsiFromValue() {
    return (GrTypeDefinition)super.extractPsiFromValue();
  }

  @javax.annotation.Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Module module = getModule();

    final GrTypeDefinition grTypeDefinition = extractPsiFromValue();
    assert grTypeDefinition != null;

    buildChildren(module, grTypeDefinition, children);

    return children.isEmpty() ? null : children;
  }

  protected void buildChildren(final Module module, final GrTypeDefinition grClass, final List<AbstractTreeNode> children) {
    final String parentLocationRootMark = getValue().getLocationRootMark();

    final GrMethod[] methods = grClass.getCodeMethods();
    for (final GrMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) continue;

      final MethodNode node = createNodeForMethod(module, method, parentLocationRootMark);
      if (node != null) children.add(node);
    }
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @javax.annotation.Nullable
  protected MethodNode createNodeForMethod(final Module module, final GrMethod method, final String parentLocationRootMark) {
    return null;
  }

}