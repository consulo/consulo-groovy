package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.util.function.Processor;
import consulo.language.psi.PsiReference;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;

/**
* @author peter
*/
public class LiteralConstructorSearcher {
  private final PsiMethod myConstructor;
  private final Processor<? super PsiReference> myConsumer;
  private final boolean myIncludeOverloads;

  public LiteralConstructorSearcher(PsiMethod constructor, Processor<? super PsiReference> consumer, boolean includeOverloads) {
    myConstructor = constructor;
    myConsumer = consumer;
    myIncludeOverloads = includeOverloads;
  }

  public boolean processLiteral(GrListOrMap literal) {
    final PsiReference reference = literal.getReference();
    if (reference instanceof LiteralConstructorReference) {
      if (isCorrectReference((LiteralConstructorReference)reference) && !myConsumer.process(reference)) {
        return false;
      }
    }
    return true;
  }

  private boolean isCorrectReference(LiteralConstructorReference reference) {
    if (reference.isReferenceTo(myConstructor)) {
      return true;
    }

    if (!myIncludeOverloads) {
      return false;
    }

    final PsiClass psiClass = reference.getConstructedClassType().resolve();
    return myConstructor.getManager().areElementsEquivalent(myConstructor.getContainingClass(), psiClass);
  }
}
