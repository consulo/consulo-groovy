package org.jetbrains.plugins.groovy.geb;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.pom.PomDeclarationSearcher;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Sergey Evdokimov
 */
public class GebContentDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@Nonnull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    PsiElement grCall = element.getParent();
    if (!(grCall instanceof GrMethodCall)) return;

    PsiElement grClosure = grCall.getParent();
    if (!(grClosure instanceof GrClosableBlock)) return;

    PsiElement contentField = grClosure.getParent();
    if (!(contentField instanceof GrField)) return;

    GrField field = (GrField)contentField;
    if (!"content".equals(field.getName()) || !field.hasModifierProperty(PsiModifier.STATIC)) return;

    PsiClass containingClass = field.getContainingClass();
    if (!GroovyPsiManager.isInheritorCached(containingClass, "geb.Page")
      && !GroovyPsiManager.isInheritorCached(containingClass, "geb.Module")) return;

    Map<String, PsiField> elements = GebUtil.getContentElements(containingClass);

    for (PsiField f : elements.values()) {
      if (f.getNavigationElement() == element) {
        consumer.accept(f);
        return;
      }
    }
  }

}
