package org.jetbrains.plugins.groovy.mvc.projectView;

import javax.annotation.Nonnull;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import icons.JetgroovyIcons;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Dmitry Krasilschikov
 */
public class DomainClassNode extends ClassNode {
  public DomainClassNode(@Nonnull final Module module,
                         @Nonnull final GrTypeDefinition typeDefinition,
                         @javax.annotation.Nullable final ViewSettings viewSettings) {
    super(module, typeDefinition, NodeId.DOMAIN_CLASS_IN_DOMAINS_SUBTREE, viewSettings);
  }

  @Override
  protected String getTestPresentationImpl(@Nonnull final NodeId nodeId, @Nonnull final PsiElement psiElement) {
    return "Domain class: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected void updateImpl(final PresentationData data) {
    super.updateImpl(data);
    data.setIcon(JetgroovyIcons.Mvc.Domain_class);
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    return getValue() != null;
  }

}
