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
package org.jetbrains.plugins.groovy.impl.refactoring.rename;

import com.intellij.java.impl.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.util.PropertyUtil;
import com.intellij.java.language.psi.util.PsiFormatUtil;
import com.intellij.java.language.psi.util.PsiFormatUtilBase;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.groovy.impl.localize.GroovyRefactoringLocalize;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.rename.UnresolvableCollisionUsageInfo;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.usage.MoveRenameUsageInfo;
import consulo.usage.UsageInfo;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MethodResolverProcessor;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.findGetterForField;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.findSetterForField;

/**
 * @author ilyas
 */
@ExtensionImpl(id = "groovyFieldRenameProcessor")
public class RenameGrFieldProcessor extends RenameJavaVariableProcessor {

    @Nonnull
    @Override
    public Collection<PsiReference> findReferences(PsiElement element) {
        assert element instanceof GrField;

        List<PsiReference> refs = new ArrayList<>();

        GrField field = (GrField) element;
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        PsiMethod setter = field.getSetter();
        if (setter != null) {
            refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(
                MethodReferencesSearch.search(setter, projectScope, true).findAll(),
                setter
            ));
        }
        GrAccessorMethod[] getters = field.getGetters();
        for (GrAccessorMethod getter : getters) {
            refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(
                MethodReferencesSearch.search(getter, projectScope, true).findAll(),
                getter
            ));
        }
        refs.addAll(RenameAliasedUsagesUtil.filterAliasedRefs(ReferencesSearch.search(field, projectScope, true).findAll(), field));
        return refs;
    }

    @Override
    @RequiredWriteAction
    public void renameElement(
        PsiElement psiElement,
        String newName,
        UsageInfo[] usages,
        @Nullable RefactoringElementListener listener
    ) throws IncorrectOperationException {
        GrField field = (GrField) psiElement;
        String fieldName = field.getName();
        Map<PsiElement, String> renames = new HashMap<>();
        renames.put(field, newName);

        for (GrAccessorMethod getter : field.getGetters()) {
            renames.put(getter, RenamePropertyUtil.getGetterNameByOldName(newName, getter.getName()));
        }
        GrAccessorMethod setter = field.getSetter();
        if (setter != null) {
            renames.put(setter, GroovyPropertyUtils.getSetterName(newName));
        }

        MultiMap<PsiNamedElement, UsageInfo> propertyUsages = MultiMap.createLinked();
        MultiMap<PsiNamedElement, UsageInfo> simpleUsages = MultiMap.createLinked();

        List<PsiReference> unknownUsages = new ArrayList<>();

        for (UsageInfo usage : usages) {
            PsiReference ref = usage.getReference();
            if (ref instanceof GrReferenceExpression refExpr) {
                GroovyResolveResult resolveResult = refExpr.advancedResolve();
                PsiElement element = resolveResult.getElement();
                if (resolveResult.isInvokedOnProperty()) {
                    if (element == null) {
                        unknownUsages.add(refExpr);
                    }
                    else {
                        propertyUsages.putValue((PsiNamedElement) element, usage);
                    }
                }
                else {
                    simpleUsages.putValue((PsiNamedElement) element, usage);
                }
            }
            else if (ref != null) {
                unknownUsages.add(ref);
            }
        }

        for (PsiReference ref : unknownUsages) {
            handleElementRename(newName, ref, fieldName);
        }

        field.setName(newName);

        GrAccessorMethod[] newGetters = field.getGetters();
        GrAccessorMethod newSetter = field.getSetter();
        Map<String, PsiNamedElement> newElements = new HashMap<>();
        newElements.put(newName, field);
        for (GrAccessorMethod newGetter : newGetters) {
            newElements.put(newGetter.getName(), newGetter);
        }
        if (newSetter != null) {
            newElements.put(newSetter.getName(), newSetter);
        }

        PsiManager manager = field.getManager();
        for (PsiNamedElement element : simpleUsages.keySet()) {
            for (UsageInfo info : simpleUsages.get(element)) {
                String name = renames.get(element);
                rename(newElements.get(name), info, name == null ? newName : name, name != null, manager);
            }
        }
        for (PsiNamedElement element : propertyUsages.keySet()) {
            for (UsageInfo info : propertyUsages.get(element)) {
                rename(element, info, newName, true, manager);
            }
        }
        if (listener != null) {
            listener.elementRenamed(field);
        }
    }

    @RequiredWriteAction
    private static void rename(
        PsiNamedElement element,
        UsageInfo info,
        String nameToUse,
        boolean shouldCheckForCorrectResolve,
        PsiManager manager
    ) {
        PsiReference ref = info.getReference();
        PsiElement renamed = ((GrReferenceExpression) ref).handleElementRenameSimple(nameToUse);
        PsiElement newlyResolved = ref.resolve();
        if (shouldCheckForCorrectResolve) {
            if (element instanceof GrAccessorMethod oldAccessor && newlyResolved instanceof GrAccessorMethod newAccessor) {
                if (!manager.areElementsEquivalent(oldAccessor.getProperty(), newAccessor.getProperty())
                    && oldAccessor.isSetter() == newAccessor.isSetter()) {
                    qualify(oldAccessor, renamed, nameToUse);
                }
            }
            else if (!manager.areElementsEquivalent(element, newlyResolved)) {
                qualify((PsiMember) element, renamed, nameToUse);
            }
        }
    }

    @RequiredWriteAction
    private static void handleElementRename(String newName, PsiReference ref, String fieldName) {
        String refText = ref instanceof PsiQualifiedReference qRef ? qRef.getReferenceName() : ref.getCanonicalText();

        String toRename;
        if (fieldName.equals(refText)) {
            toRename = newName;
        }
        else if (GroovyPropertyUtils.getGetterNameNonBoolean(fieldName).equals(refText)) {
            toRename = GroovyPropertyUtils.getGetterNameNonBoolean(newName);
        }
        else if (GroovyPropertyUtils.getGetterNameBoolean(fieldName).equals(refText)) {
            toRename = GroovyPropertyUtils.getGetterNameBoolean(newName);
        }
        else if (GroovyPropertyUtils.getSetterName(fieldName).equals(refText)) {
            toRename = GroovyPropertyUtils.getSetterName(newName);
        }
        else {
            toRename = newName;
        }
        ref.handleElementRename(toRename);
    }

    @RequiredWriteAction
    private static void qualify(PsiMember member, PsiElement renamed, String name) {
        if (!(renamed instanceof GrReferenceExpression refExpr)) {
            return;
        }

        PsiClass clazz = member.getContainingClass();
        if (clazz == null) {
            return;
        }

        if (refExpr.getQualifierExpression() != null) {
            return;
        }

        PsiElement replaced;
        if (member.isStatic()) {
            GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
                .createReferenceExpressionFromText(clazz.getQualifiedName() + "." + name);
            replaced = refExpr.replace(newRefExpr);
        }
        else {
            PsiClass containingClass = PsiTreeUtil.getParentOfType(renamed, PsiClass.class);
            if (member.getManager().areElementsEquivalent(containingClass, clazz)) {
                GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
                    .createReferenceExpressionFromText("this." + name);
                replaced = refExpr.replace(newRefExpr);
            }
            else {
                GrReferenceExpression newRefExpr = GroovyPsiElementFactory.getInstance(member.getProject())
                    .createReferenceExpressionFromText(clazz.getQualifiedName() + ".this." + name);
                replaced = refExpr.replace(newRefExpr);
            }
        }
        JavaCodeStyleManager.getInstance(replaced.getProject()).shortenClassReferences(replaced);
    }

    @Override
    public boolean canProcessElement(@Nonnull PsiElement element) {
        return element instanceof GrField field && field.isProperty() || element instanceof GrAccessorMethod;
    }

    @Override
    @RequiredReadAction
    public void findCollisions(
        PsiElement element,
        String newName,
        Map<? extends PsiElement, String> allRenames,
        List<UsageInfo> result
    ) {
        List<UsageInfo> collisions = new ArrayList<>();

        for (UsageInfo info : result) {
            if (!(info instanceof MoveRenameUsageInfo moveRenameUsageInfo)) {
                continue;
            }
            PsiElement infoElement = moveRenameUsageInfo.getElement();
            PsiElement referencedElement = moveRenameUsageInfo.getReferencedElement();

            if (!(infoElement instanceof GrReferenceExpression refExpr)) {
                continue;
            }

            if (!(referencedElement instanceof GrField || refExpr.advancedResolve().isInvokedOnProperty())) {
                continue;
            }

            if (!(refExpr.getParent() instanceof GrCall)) {
                continue;
            }

            PsiType[] argTypes = PsiUtil.getArgumentTypes(refExpr, false);
            PsiType[] typeArguments = refExpr.getTypeArguments();
            MethodResolverProcessor processor = new MethodResolverProcessor(newName, refExpr, false, null, argTypes, typeArguments);
            final PsiMethod resolved = ResolveUtil.resolveExistingElement(refExpr, processor, PsiMethod.class);
            if (resolved == null) {
                continue;
            }

            collisions.add(new UnresolvableCollisionUsageInfo(resolved, refExpr) {
                @Nonnull
                @Override
                @RequiredReadAction
                public LocalizeValue getDescription() {
                    return GroovyRefactoringLocalize.usageWillBeOverridenByMethod(
                        refExpr.getParent().getText(),
                        PsiFormatUtil.formatMethod(resolved, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME, PsiFormatUtilBase.SHOW_TYPE)
                    );
                }
            });
        }
        result.addAll(collisions);
        super.findCollisions(element, newName, allRenames, result);
    }

    @Override
    public void findExistingNameConflicts(PsiElement element, String newName, MultiMap<PsiElement, LocalizeValue> conflicts) {
        super.findExistingNameConflicts(element, newName, conflicts);

        GrField field = (GrField) element;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null) {
            return;
        }

        if (findGetterForField(field) instanceof GrAccessorMethod) {
            PsiMethod newGetter =
                PropertyUtil.findPropertyGetter(containingClass, newName, field.isStatic(), true);
            if (newGetter != null && !(newGetter instanceof GrAccessorMethod)) {
                conflicts.putValue(
                    newGetter,
                    GroovyRefactoringLocalize.implicitGetterWillByOverridenByMethod(field.getName(), newGetter.getName())
                );
            }
        }
        if (findSetterForField(field) instanceof GrAccessorMethod) {
            PsiMethod newSetter = PropertyUtil.findPropertySetter(containingClass, newName, field.isStatic(), true);
            if (newSetter != null && !(newSetter instanceof GrAccessorMethod)) {
                conflicts.putValue(
                    newSetter,
                    GroovyRefactoringLocalize.implicitSetterWillByOverridenByMethod(field.getName(), newSetter.getName())
                );
            }
        }
    }
}
