package org.jetbrains.plugins.groovy.impl.codeInspection.style;

import consulo.groovy.impl.localize.GroovyInspectionLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.utils.JavaStylePropertiesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class JavaStylePropertiesInvocationInspection extends BaseInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Java-style property access");
    }

    @Override
    protected BaseInspectionVisitor buildVisitor() {
        return new BaseInspectionVisitor() {
            @Override
            public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
                super.visitMethodCallExpression(methodCallExpression);
                visitMethodCall(methodCallExpression);
            }

            @Override
            public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
                super.visitApplicationStatement(applicationStatement);
                visitMethodCall(applicationStatement);
            }

            private void visitMethodCall(GrMethodCall methodCall) {
                if (JavaStylePropertiesUtil.isPropertyAccessor(methodCall)) {
                    LocalizeValue message = GroovyInspectionLocalize.javaStylePropertyAccess();
                    final GrExpression expression = methodCall.getInvokedExpression();
                    if (expression instanceof GrReferenceExpression) {
                        PsiElement referenceNameElement = ((GrReferenceExpression) expression).getReferenceNameElement();
                        registerError(referenceNameElement, message, myFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }
                }
            }
        };
    }

    private static final LocalQuickFix[] myFixes = new LocalQuickFix[]{new JavaStylePropertiesInvocationFixer()};
}
