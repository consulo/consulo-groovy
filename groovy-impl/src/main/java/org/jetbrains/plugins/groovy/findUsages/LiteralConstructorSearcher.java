package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

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
