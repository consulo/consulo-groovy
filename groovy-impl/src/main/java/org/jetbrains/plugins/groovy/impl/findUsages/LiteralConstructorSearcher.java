package org.jetbrains.plugins.groovy.impl.findUsages;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.PsiReference;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;

import java.util.function.Predicate;

/**
 * @author peter
 */
public class LiteralConstructorSearcher {
    private final PsiMethod myConstructor;
    private final Predicate<? super PsiReference> myConsumer;
    private final boolean myIncludeOverloads;

    public LiteralConstructorSearcher(PsiMethod constructor, Predicate<? super PsiReference> consumer, boolean includeOverloads) {
        myConstructor = constructor;
        myConsumer = consumer;
        myIncludeOverloads = includeOverloads;
    }

    public boolean processLiteral(GrListOrMap literal) {
      return !(literal.getReference() instanceof LiteralConstructorReference literalConstructorReference
            && isCorrectReference(literalConstructorReference) && !myConsumer.test(literalConstructorReference));
    }

    private boolean isCorrectReference(LiteralConstructorReference reference) {
        if (reference.isReferenceTo(myConstructor)) {
            return true;
        }

        if (!myIncludeOverloads) {
            return false;
        }

        PsiClass psiClass = reference.getConstructedClassType().resolve();
        return myConstructor.getManager().areElementsEquivalent(myConstructor.getContainingClass(), psiClass);
    }
}
