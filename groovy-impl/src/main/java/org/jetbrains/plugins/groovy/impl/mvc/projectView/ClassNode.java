package org.jetbrains.plugins.groovy.impl.mvc.projectView;

import consulo.module.Module;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.project.ui.view.tree.ViewSettings;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Krasilschikov
 */
public class ClassNode extends AbstractMvcPsiNodeDescriptor {
  public ClassNode(@Nonnull final Module module,
                   @Nonnull final GrTypeDefinition rClass,
                   @Nullable final ViewSettings viewSettings) {
    super(module, viewSettings, rClass, CLASS);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final PsiElement psiElement) {
    return "GrTypeDefinition: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected GrTypeDefinition extractPsiFromValue() {
    return (GrTypeDefinition)super.extractPsiFromValue();
  }

  @Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Module module = getModule();

    final GrTypeDefinition grTypeDefinition = extractPsiFromValue();
    assert grTypeDefinition != null;

    buildChildren(module, grTypeDefinition, children);

    return children.isEmpty() ? null : children;
  }

  protected void buildChildren(final Module module, final GrTypeDefinition grClass, final List<AbstractTreeNode> children) {
    final GrMethod[] methods = grClass.getCodeMethods();
    for (final GrMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) continue;

      final MethodNode node = createNodeForMethod(module, method);
      if (node != null) children.add(node);
    }
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Nullable
  protected MethodNode createNodeForMethod(final Module module, final GrMethod method) {
    return null;
  }

}