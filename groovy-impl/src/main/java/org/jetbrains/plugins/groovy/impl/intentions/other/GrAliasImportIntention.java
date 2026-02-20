/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.intentions.other;

import com.intellij.java.indexing.search.searches.MethodReferencesSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Computable;
import consulo.application.util.function.Processor;
import consulo.codeEditor.Editor;
import consulo.component.extension.Extensions;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.groovy.impl.localize.GroovyIntentionLocalize;
import consulo.language.codeStyle.PostprocessReformattingAspect;
import consulo.language.editor.refactoring.rename.NameSuggestionProvider;
import consulo.language.editor.refactoring.rename.PreferrableNameSuggestionProvider;
import consulo.language.editor.refactoring.rename.SuggestedNameInfo;
import consulo.language.editor.refactoring.rename.inplace.MyLookupExpression;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.event.TemplateEditingAdapter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrAliasImportIntention extends Intention {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return GroovyIntentionLocalize.grAliasImportIntentionName();
    }

    @Override
    protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
        GrImportStatement context;
        PsiMember resolved;
        if (element instanceof GrReferenceExpression) {
            GrReferenceExpression ref = (GrReferenceExpression) element;

            GroovyResolveResult result = ref.advancedResolve();
            context = (GrImportStatement) result.getCurrentFileResolveContext();
            assert context != null;
            resolved = (PsiMember) result.getElement();
        }
        else if (element instanceof GrImportStatement) {
            context = (GrImportStatement) element;
            resolved = (PsiMember) context.getImportReference().resolve();
        }
        else {
            return;
        }

        assert resolved != null;
        doRefactoring(project, context, resolved);
    }

    private static void doRefactoring(@Nonnull Project project, @Nonnull GrImportStatement importStatement, @Nonnull PsiMember member) {
        if (member instanceof GrAccessorMethod &&
            !importStatement.isOnDemand() &&
            !importStatement.getImportedName().equals(member.getName())) {
            member = ((GrAccessorMethod) member).getProperty();
        }

        GroovyFileBase file = (GroovyFileBase) importStatement.getContainingFile();
        List<UsageInfo> usages = findUsages(member, file);
        GrImportStatement templateImport = createTemplateImport(project, importStatement, member, file);

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            if (!importStatement.isOnDemand()) {
                importStatement.delete();
            }
            updateRefs(usages, member.getName(), templateImport);
        }
        else {
            runTemplate(project, importStatement, member, file, usages, templateImport);
        }
    }

    private static GrImportStatement createTemplateImport(
        Project project,
        GrImportStatement context,
        PsiMember resolved,
        GroovyFileBase file
    ) {
        PsiClass aClass = resolved.getContainingClass();
        assert aClass != null;
        String qname = aClass.getQualifiedName();
        String name = resolved.getName();

        GrImportStatement template = GroovyPsiElementFactory.getInstance(project).createImportStatementFromText
            ("import static " + qname + "." + name + " as aliased");
        return file.addImport(template);
    }

    private static void runTemplate(
        Project project,
        GrImportStatement context,
        PsiMember resolved,
        final GroovyFileBase file,
        final List<UsageInfo> usages,
        GrImportStatement templateImport
    ) {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();

        TemplateBuilder templateBuilder = TemplateBuilderFactory.getInstance().createTemplateBuilder(templateImport);

        LinkedHashSet<String> names = getSuggestedNames(resolved, context);

        PsiElement aliasNameElement = templateImport.getAliasNameElement();
        assert aliasNameElement != null;
        templateBuilder.replaceElement(aliasNameElement, new MyLookupExpression(resolved.getName(), names,
            (PsiNamedElement) resolved, resolved, true, null
        ));
        Template built = templateBuilder.buildTemplate();

        Editor newEditor = IntentionUtils.positionCursor(project, file, templateImport);
        Document document = newEditor.getDocument();

        final RangeMarker contextImportPointer = document.createRangeMarker(context.getTextRange());

        final TextRange range = templateImport.getTextRange();
        document.deleteString(range.getStartOffset(), range.getEndOffset());

        final String name = resolved.getName();

        TemplateManager manager = TemplateManager.getInstance(project);
        manager.startTemplate(newEditor, built, new TemplateEditingAdapter() {
            @Override
            public void templateFinished(Template template, boolean brokenOff) {
                final GrImportStatement importStatement = ApplicationManager.getApplication().runReadAction(
                    new Computable<GrImportStatement>() {
                        @Nullable
                        @Override
                        public GrImportStatement compute() {
                            return PsiTreeUtil.findElementOfClassAtOffset(
                                file,
                                range.getStartOffset(),
                                GrImportStatement.class,
                                true
                            );
                        }
                    });

                if (brokenOff) {
                    if (importStatement != null) {
                        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                            @Override
                            public void run() {
                                importStatement.delete();
                            }
                        });
                    }
                    return;
                }

                updateRefs(usages, name, importStatement);

                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    @Override
                    public void run() {
                        GrImportStatement context = PsiTreeUtil.findElementOfClassAtRange(
                            file,
                            contextImportPointer.getStartOffset(),
                            contextImportPointer.getEndOffset(),
                            GrImportStatement.class
                        );
                        if (context != null) {
                            context.delete();
                        }
                    }
                });
            }
        });
    }

    private static void updateRefs(List<UsageInfo> usages, final String memberName, final GrImportStatement updatedImport) {
        if (updatedImport == null) {
            return;
        }

        final String name = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Nullable
            @Override
            public String compute() {
                return updatedImport.getImportedName();
            }
        });

        for (final UsageInfo usage : usages) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                    PsiElement usageElement = usage.getElement();
                    if (usageElement == null) {
                        return;
                    }

                    if (usageElement.getParent() instanceof GrImportStatement) {
                        return;
                    }

                    if (usageElement instanceof GrReferenceElement) {
                        GrReferenceElement ref = (GrReferenceElement) usageElement;
                        PsiElement qualifier = ref.getQualifier();

                        if (qualifier == null) {
                            String refName = ref.getReferenceName();
                            if (refName == null) {
                                return;
                            }

                            if (memberName.equals(refName)) {
                                ref.handleElementRenameSimple(name);
                            }
                            else if (refName.equals(GroovyPropertyUtils.getPropertyNameByAccessorName(memberName))) {
                                String newPropName = GroovyPropertyUtils.getPropertyNameByAccessorName(name);
                                if (newPropName != null) {
                                    ref.handleElementRenameSimple(newPropName);
                                }
                                else {
                                    ref.handleElementRenameSimple(name);
                                }
                            }
                            else if (refName.equals(GroovyPropertyUtils.getGetterNameBoolean(memberName))) {
                                String getterName = GroovyPropertyUtils.getGetterNameBoolean(name);
                                ref.handleElementRenameSimple(getterName);
                            }
                            else if (refName.equals(GroovyPropertyUtils.getGetterNameNonBoolean(memberName))) {
                                String getterName = GroovyPropertyUtils.getGetterNameNonBoolean(name);
                                ref.handleElementRenameSimple(getterName);
                            }
                            else if (refName.equals(GroovyPropertyUtils.getSetterName(memberName))) {
                                String getterName = GroovyPropertyUtils.getSetterName(name);
                                ref.handleElementRenameSimple(getterName);
                            }
                        }
                    }
                }
            });
        }
    }

    private static List<UsageInfo> findUsages(PsiMember member, GroovyFileBase file) {
        LocalSearchScope scope = new LocalSearchScope(file);

        final ArrayList<UsageInfo> infos = new ArrayList<UsageInfo>();
        final HashSet<Object> usedRefs = new HashSet<>();

        Processor<PsiReference> consumer = new Processor<PsiReference>() {
            @Override
            public boolean process(PsiReference reference) {
                if (usedRefs.add(reference)) {
                    infos.add(new UsageInfo(reference));
                }

                return true;
            }
        };

        if (member instanceof PsiMethod) {
            MethodReferencesSearch.search((PsiMethod) member, scope, false).forEach(consumer);
        }
        else {
            ReferencesSearch.search(member, scope).forEach(consumer);
            if (member instanceof PsiField) {
                PsiMethod getter = GroovyPropertyUtils.findGetterForField((PsiField) member);
                if (getter != null) {
                    MethodReferencesSearch.search(getter, scope, false).forEach(consumer);
                }
                PsiMethod setter = GroovyPropertyUtils.findSetterForField((PsiField) member);
                if (setter != null) {
                    MethodReferencesSearch.search(setter, scope, false).forEach(consumer);
                }
            }
        }

        return infos;
    }

    public static LinkedHashSet<String> getSuggestedNames(
        PsiElement psiElement,
        PsiElement nameSuggestionContext
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        result.add(UsageViewUtil.getShortName(psiElement));
        NameSuggestionProvider[] providers = Extensions.getExtensions(NameSuggestionProvider.EP_NAME);
        for (NameSuggestionProvider provider : providers) {
            SuggestedNameInfo info = provider.getSuggestedNames(psiElement, nameSuggestionContext, result);
            if (info != null) {
                if (provider instanceof PreferrableNameSuggestionProvider && !((PreferrableNameSuggestionProvider)
                    provider).shouldCheckOthers()) {
                    break;
                }
            }
        }
        return result;
    }


    @Nonnull
    @Override
    protected PsiElementPredicate getElementPredicate() {
        return new PsiElementPredicate() {
            @Override
            public boolean satisfiedBy(PsiElement element) {
                if (element instanceof GrReferenceExpression) {

                    GroovyResolveResult result = ((GrReferenceExpression) element).advancedResolve();

                    PsiElement context = result.getCurrentFileResolveContext();
                    if (!(context instanceof GrImportStatement)) {
                        return false;
                    }

                    GrImportStatement importStatement = (GrImportStatement) context;
                    if (!importStatement.isStatic() || importStatement.isAliasedImport()) {
                        return false;
                    }

                    return true;
                }
                else if (element instanceof GrImportStatement) {
                    GrImportStatement importStatement = (GrImportStatement) element;
                    if (!importStatement.isStatic()) {
                        return false;
                    }
                    if (importStatement.isOnDemand()) {
                        return false;
                    }
                    if (importStatement.isAliasedImport()) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        };
    }
}
