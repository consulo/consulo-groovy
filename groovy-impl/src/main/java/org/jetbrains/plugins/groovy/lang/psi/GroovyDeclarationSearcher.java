package org.jetbrains.plugins.groovy.lang.psi;

import consulo.language.pom.PomDeclarationSearcher;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * @author peter
 */
public class GroovyDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@Nonnull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    if (element instanceof GrTypeDefinition) {
      final PsiElement name = ((GrTypeDefinition)element).getNameIdentifierGroovy();
      if (name.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).contains(offsetInElement)) {
        consumer.accept((GrTypeDefinition)element);
      }
    }
  }
}
