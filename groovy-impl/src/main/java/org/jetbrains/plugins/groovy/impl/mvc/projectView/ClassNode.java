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
  public ClassNode(@Nonnull Module module,
                   @Nonnull GrTypeDefinition rClass,
                   @Nullable ViewSettings viewSettings) {
    super(module, viewSettings, rClass, CLASS);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull PsiElement psiElement) {
    return "GrTypeDefinition: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected GrTypeDefinition extractPsiFromValue() {
    return (GrTypeDefinition)super.extractPsiFromValue();
  }

  @Nullable
  protected Collection<AbstractTreeNode> getChildrenImpl() {
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    Module module = getModule();

    GrTypeDefinition grTypeDefinition = extractPsiFromValue();
    assert grTypeDefinition != null;

    buildChildren(module, grTypeDefinition, children);

    return children.isEmpty() ? null : children;
  }

  protected void buildChildren(Module module, GrTypeDefinition grClass, List<AbstractTreeNode> children) {
    GrMethod[] methods = grClass.getCodeMethods();
    for (GrMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) continue;

      MethodNode node = createNodeForMethod(module, method);
      if (node != null) children.add(node);
    }
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }

  @Nullable
  protected MethodNode createNodeForMethod(Module module, GrMethod method) {
    return null;
  }

}