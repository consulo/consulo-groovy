/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.impl.intentions.style;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.codeStyle.GrReferenceAdjuster;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;

/**
 * @author Maxim.Medvedev
 */
public class ImportStaticIntention extends Intention {
    private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<>("TEMP_REFERENT_USER_DATA");

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.importStaticIntentionName();
    }

    @Override
    @RequiredWriteAction
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        if (!(element instanceof GrReferenceExpression ref)) {
            return;
        }
        if (!(ref.resolve() instanceof PsiMember member)) {
            return;
        }

        PsiClass containingClass = member.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String qname = containingClass.getQualifiedName();
        String name = member.getName();
        if (name == null) {
            return;
        }

        if (!(element.getContainingFile() instanceof GroovyFile file)) {
            return;
        }
        file.accept(new GroovyRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitReferenceExpression(GrReferenceExpression expression) {
                super.visitReferenceExpression(expression);
                if (name.equals(expression.getReferenceName())) {
                    PsiElement resolved = expression.resolve();
                    if (resolved != null) {
                        expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
                    }
                }
            }
        });

        GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
        GrImportStatement tempImport = factory.createImportStatementFromText(qname + "." + name, true, false, null);
        GrImportStatement importStatement = file.addImport(tempImport);

        boolean isAnythingShortened = false;
        for (PsiReference reference : ReferencesSearch.search(member, new LocalSearchScope(file))) {
            PsiElement refElement = reference.getElement();
            if (refElement instanceof GrQualifiedReference<?>) {
                isAnythingShortened |= GrReferenceAdjuster.shortenReference((GrQualifiedReference<?>) refElement);
            }
        }

        if (!isAnythingShortened) {
            importStatement.delete();
            return;
        }

        file.accept(new GroovyRecursiveElementVisitor() {
            @Override
            @RequiredWriteAction
            public void visitReferenceExpression(GrReferenceExpression expression) {
                super.visitReferenceExpression(expression);

                GrTypeArgumentList typeArgumentList = expression.getTypeArgumentList();
                if (typeArgumentList != null && typeArgumentList.getFirstChild() != null) {
                    expression.putUserData(TEMP_REFERENT_USER_DATA, null);

                    return;
                }

                if (name.equals(expression.getReferenceName())) {
                    if (expression.isQualified()) {
                        if (expression.getQualifierExpression() instanceof GrReferenceExpression ref
                            && ref.resolve() == member.getContainingClass()) {
                            GrReferenceAdjuster.shortenReference(expression);
                        }
                    }
                    else if (expression.getUserData(TEMP_REFERENT_USER_DATA) instanceof PsiMember member
                        && member.isStatic()
                        && member != expression.resolve()) {
                        expression.bindToElement(member);
                    }
                }
                expression.putUserData(TEMP_REFERENT_USER_DATA, null);
            }
        });
    }

    @Override
    protected boolean isStopElement(PsiElement element) {
        return super.isStopElement(element) || element instanceof GrReferenceExpression;
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return element -> element instanceof GrReferenceExpression ref
            && ref.getQualifier() != null
            && ref.resolve() instanceof PsiMember member
            && !(member instanceof PsiClass)
            && member.isStatic()
            && member.getContainingClass() != null;
    }
}
