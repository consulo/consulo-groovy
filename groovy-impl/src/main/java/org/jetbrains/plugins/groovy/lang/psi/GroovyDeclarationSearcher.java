package org.jetbrains.plugins.groovy.lang.psi;

import javax.annotation.Nonnull;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author peter
 */
public class GroovyDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@Nonnull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    if (element instanceof GrTypeDefinition) {
      final PsiElement name = ((GrTypeDefinition)element).getNameIdentifierGroovy();
      if (name.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).contains(offsetInElement)) {
		  consumer.consume((GrTypeDefinition) element);
      }
    }
  }
}
