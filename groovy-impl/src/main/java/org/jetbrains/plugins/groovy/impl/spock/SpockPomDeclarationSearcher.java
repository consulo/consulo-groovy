package org.jetbrains.plugins.groovy.impl.spock;

import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.pom.PomDeclarationSearcher;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Sergey Evdokimov
 */
@ExtensionImpl
public class SpockPomDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@Nonnull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    String name = SpockUtils.getNameByReference(element);
    if (name == null) return;

    GrMethod method = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (method == null) return;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;

    if (!GroovyPsiManager.isInheritorCached(containingClass, SpockUtils.SPEC_CLASS_NAME)) return;

    Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

    SpockVariableDescriptor descriptor = cachedValue.get(name);
    if (descriptor == null) return;

    if (descriptor.getNavigationElement() == element) {
      consumer.accept(descriptor.getVariable());
    }
  }
}
