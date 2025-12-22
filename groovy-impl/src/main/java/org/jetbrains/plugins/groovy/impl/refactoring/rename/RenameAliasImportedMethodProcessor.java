/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.rename;

import com.intellij.java.impl.refactoring.rename.RenameJavaMethodProcessor;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiReferenceExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.localize.RefactoringLocalize;
import consulo.language.editor.refactoring.rename.RenameDialog;
import consulo.language.editor.refactoring.rename.RenameUtil;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.usage.UsageInfo;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.*;

/**
 * @author Maxim.Medvedev
 */
@ExtensionImpl
public class RenameAliasImportedMethodProcessor extends RenameJavaMethodProcessor {
    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof GroovyPsiElement && super.canProcessElement(element);
    }

    @Nonnull
    @Override
    public Collection<PsiReference> findReferences(PsiElement element) {
        return RenameAliasedUsagesUtil.filterAliasedRefs(super.findReferences(element), element);
    }

    @Override
    @RequiredUIAccess
    public RenameDialog createRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
        return new RenameDialog(project, element, nameSuggestionContext, editor) {
            @Override
            @RequiredReadAction
            protected boolean areButtonsValid() {
                return true;
            }
        };
    }

    @Override
    @RequiredWriteAction
    public void renameElement(
        PsiElement psiElement,
        String newName,
        UsageInfo[] usages,
        @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        boolean isGetter = isSimplePropertyGetter((PsiMethod) psiElement);
        boolean isSetter = isSimplePropertySetter((PsiMethod) psiElement);

        List<UsageInfo> methodAccess = new ArrayList<>(usages.length);
        List<UsageInfo> propertyAccess = new ArrayList<>(usages.length);

        for (UsageInfo usage : usages) {
            if (usage.getElement() instanceof GrReferenceExpression refExpr && refExpr.advancedResolve().isInvokedOnProperty()) {
                propertyAccess.add(usage);
            }
            else {
                methodAccess.add(usage);
            }
        }

        super.renameElement(psiElement, newName, methodAccess.toArray(new UsageInfo[methodAccess.size()]), listener);

        String propertyName;
        if (isGetter) {
            propertyName = getPropertyNameByGetterName(newName, true);
        }
        else if (isSetter) {
            propertyName = getPropertyNameBySetterName(newName);
        }
        else {
            propertyName = null;
        }

        if (propertyName == null) {
            //it means accessor is renamed to not-accessor and we should replace all property-access-refs with method-access-refs

            GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(psiElement.getProject());
            for (UsageInfo info : propertyAccess) {
                if (info.getElement() instanceof GrReferenceExpression refExpr) {
                    PsiElement qualifier = refExpr.getQualifier();
                    String qualifierPrefix = qualifier == null ? "" : qualifier.getText() + ".";
                    if (isGetter) {
                        GrExpression call = factory.createExpressionFromText(qualifierPrefix + newName + "()");
                        refExpr.replaceWithExpression(call, true);
                    }
                    else {
                        PsiElement parent = refExpr.getParent();
                        assert parent instanceof GrAssignmentExpression;
                        GrExpression rValue = ((GrAssignmentExpression) parent).getRValue();
                        GrExpression call =
                            factory.createExpressionFromText(qualifierPrefix + newName + "(" + (rValue == null ? "" : rValue.getText()) + ")");
                        ((GrAssignmentExpression) parent).replaceWithExpression(call, true);
                    }
                }
            }
        }
        else {
            for (UsageInfo usage : propertyAccess) {
                PsiReference ref = usage.getReference();
                if (ref != null) {
                    ((GrReferenceExpression) ref).handleElementRenameSimple(propertyName);
                }
            }
        }
    }

    @Override
    @RequiredReadAction
    public void findCollisions(
        PsiElement element,
        final String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        if (element instanceof PsiMethod method) {
            OverridingMethodsSearch.search(method, method.getUseScope(), true).forEach(overrider -> {
                PsiElement original = overrider;
                if (overrider instanceof PsiMirrorElement mirrorElem) {
                    original = mirrorElem.getPrototype();
                }

                if (original instanceof SyntheticElement) {
                    return true;
                }

                if (original instanceof GrField field) {
                    result.add(new FieldNameCollisionInfo(field, method));
                }
                return true;
            });
        }

        ListIterator<UsageInfo> iterator = result.listIterator();
        while (iterator.hasNext()) {
            UsageInfo info = iterator.next();
            final PsiElement ref = info.getElement();
            if (ref instanceof GrReferenceExpression || ref == null) {
                continue;
            }
            if (!RenameUtil.isValidName(element.getProject(), ref, newName)) {
                iterator.add(new UnresolvableCollisionUsageInfo(ref, element) {
                    @Nonnull
                    @Override
                    public LocalizeValue getDescription() {
                        return RefactoringLocalize.zeroIsNotAnIdentifier(newName, ref.getText());
                    }
                });
            }
        }
    }

    @Nullable
    @Override
    @RequiredWriteAction
    protected PsiElement processRef(PsiReference ref, String newName) {
        PsiElement element = ref.getElement();
        if (RenameUtil.isValidName(element.getProject(), element, newName) || element instanceof GrReferenceElement) {
            return super.processRef(ref, newName);
        }

        PsiElement nameElement;
        if (element instanceof PsiReferenceExpression refExpr) {
            nameElement = refExpr.getReferenceNameElement();
        }
        else {
            return null;
        }
        TextRange range = nameElement.getTextRange();
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(nameElement.getContainingFile());
        document.replaceString(range.getStartOffset(), range.getEndOffset(), newName);

        return null;
    }

    private static class FieldNameCollisionInfo extends UnresolvableCollisionUsageInfo {
        private String myName;
        private String myBaseName;

        public FieldNameCollisionInfo(GrField field, PsiMethod baseMethod) {
            super(field, field);
            myName = field.getName();
            myBaseName = baseMethod.getName();
        }

        @Nonnull
        @Override
        public LocalizeValue getDescription() {
            return GroovyRefactoringLocalize.cannotRenameProperty0(myName, myBaseName);
        }
    }
}
