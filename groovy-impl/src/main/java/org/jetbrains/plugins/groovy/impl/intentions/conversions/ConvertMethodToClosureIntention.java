/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author Maxim.Medvedev
 */
public class ConvertMethodToClosureIntention extends Intention {
    private static Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.intentions.conversions.ConvertMethodToclosureIntention");

    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.convertMethodToClosureIntentionName();
    }

    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new MyPredicate();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        MultiMap<PsiElement, LocalizeValue> conflicts = new MultiMap<>();
        GrMethod method;
        if (element.getParent() instanceof GrMethod) {
            method = (GrMethod) element.getParent();
        }
        else {
            PsiReference ref = element.getReference();
            LOG.assertTrue(ref != null);
            PsiElement resolved = ref.resolve();
            LOG.assertTrue(resolved instanceof GrMethod);
            method = (GrMethod) resolved;
        }

        PsiClass containingClass = method.getContainingClass();
        String methodName = method.getName();
        PsiField field = containingClass.findFieldByName(methodName, true);

        if (field != null) {
            conflicts.putValue(field, GroovyIntentionLocalize.fieldAlreadyExists(methodName));
        }

        Collection<PsiReference> references = MethodReferencesSearch.search(method).findAll();
        Collection<GrReferenceExpression> usagesToConvert = new HashSet<GrReferenceExpression>(references.size());
        for (PsiReference ref : references) {
            PsiElement psiElement = ref.getElement();
            if (!GroovyFileType.GROOVY_LANGUAGE.equals(psiElement.getLanguage())) {
                conflicts.putValue(psiElement, GroovyIntentionLocalize.methodIsUsedOutsideOfGroovy());
            }
            else if (!PsiUtil.isMethodUsage(psiElement)) {
                if (psiElement instanceof GrReferenceExpression) {
                    if (((GrReferenceExpression) psiElement).hasMemberPointer()) {
                        usagesToConvert.add((GrReferenceExpression) psiElement);
                    }
                }
            }
        }
        if (conflicts.size() > 0) {
            ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts, (Runnable) () -> execute(method, usagesToConvert));
            conflictsDialog.show();
            if (conflictsDialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
                return;
            }
        }
        execute(method, usagesToConvert);
    }

    private static void execute(final GrMethod method, final Collection<GrReferenceExpression> usagesToConvert) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
                GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());

                StringBuilder builder = new StringBuilder(method.getTextLength());
                String modifiers = method.getModifierList().getText();
                if (modifiers.trim().length() == 0) {
                    modifiers = GrModifier.DEF;
                }
                builder.append(modifiers).append(' ');
                builder.append(method.getName()).append("={");
                builder.append(method.getParameterList().getText()).append(" ->");
                GrOpenBlock block = method.getBlock();
                builder.append(block.getText().substring(1));
                GrVariableDeclaration variableDeclaration =
                    GroovyPsiElementFactory.getInstance(method.getProject()).createFieldDeclarationFromText(builder.toString());
                method.replace(variableDeclaration);

                for (GrReferenceExpression element : usagesToConvert) {
                    PsiElement qualifier = element.getQualifier();
                    StringBuilder text = new StringBuilder(qualifier.getText());
                    element.setQualifier(null);
                    text.append('.').append(element.getText());
                    element.replace(factory.createExpressionFromText(text.toString()));
                }
            }
        });
    }

    private static class MyPredicate implements PsiElementPredicate {
        public boolean satisfiedBy(PsiElement element) {
            if (element.getLanguage() != GroovyFileType.GROOVY_LANGUAGE) {
                return false;
            }

            GrMethod method;
            PsiReference ref = element.getReference();
            if (ref != null) {
                PsiElement resolved = ref.resolve();
                if (!(resolved instanceof GrMethod)) {
                    return false;
                }
                method = (GrMethod) resolved;
            }
            else {
                PsiElement parent = element.getParent();
                if (!(parent instanceof GrMethod)) {
                    return false;
                }
                if (((GrMethod) parent).getNameIdentifierGroovy() != element) {
                    return false;
                }
                method = (GrMethod) parent;
            }
            return method.getBlock() != null && method.getParent() instanceof GrTypeDefinitionBody;
//      return element instanceof GrMethod && ((GrMethod)element).getBlock() != null && element.getParent() instanceof GrTypeDefinitionBody;
        }
    }
}


