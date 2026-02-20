package org.jetbrains.plugins.groovy.impl.lang.psi;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.pom.PomDeclarationSearcher;
import consulo.language.pom.PomTarget;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.function.Consumer;

/**
 * @author peter
 */
@ExtensionImpl
public class GroovyDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@Nonnull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    if (element instanceof GrTypeDefinition) {
      PsiElement name = ((GrTypeDefinition)element).getNameIdentifierGroovy();
      if (name.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).contains(offsetInElement)) {
        consumer.accept((GrTypeDefinition)element);
      }
    }
  }
}
