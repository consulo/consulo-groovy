package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiSubstitutor;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * Created by Max Medvedev on 15/06/14
 */
public class GrThisReferenceResolver {
    /**
     * @return null if ref is not actually 'this' reference
     */
    @Nullable
    public static GroovyResolveResult[] resolveThisExpression(@Nonnull GrReferenceExpression ref) {
        GrExpression qualifier = ref.getQualifier();

        if (qualifier == null) {
            final PsiElement parent = ref.getParent();
            if (parent instanceof GrConstructorInvocation constructorInvocation) {
                return constructorInvocation.multiResolve(false);
            }
            else {
                PsiClass aClass = PsiUtil.getContextClass(ref);
                if (aClass != null) {
                    return new GroovyResolveResultImpl[]{new GroovyResolveResultImpl(aClass, null, null, PsiSubstitutor.EMPTY, true, true)};
                }
            }
        }
        else if (qualifier instanceof GrReferenceExpression referenceExpression) {
            GroovyResolveResult result = referenceExpression.advancedResolve();
            PsiElement resolved = result.getElement();
            if (resolved instanceof PsiClass resolvedClass && PsiUtil.hasEnclosingInstanceInScope(resolvedClass, ref, false)) {
                return new GroovyResolveResult[]{result};
            }
        }

        return null;
    }
}
