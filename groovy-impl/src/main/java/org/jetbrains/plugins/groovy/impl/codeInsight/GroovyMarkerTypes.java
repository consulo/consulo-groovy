/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInsight;

import com.intellij.java.impl.codeInsight.daemon.impl.GutterIconTooltipHelper;
import com.intellij.java.impl.codeInsight.daemon.impl.LineMarkerNavigator;
import com.intellij.java.impl.codeInsight.daemon.impl.MarkerType;
import com.intellij.java.impl.ide.util.MethodOrFunctionalExpressionPresentationProvider;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.CommonProcessors;
import consulo.language.editor.localize.DaemonLocalize;
import consulo.language.editor.ui.navigation.ItemWithPresentation;
import consulo.language.editor.ui.navigation.PsiTargetNavigationService;
import consulo.language.editor.ui.navigation.TargetPresentationProvider;
import consulo.language.editor.ui.navigation.TargetUpdaterTask;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.localize.LocalizeValue;
import consulo.project.DumbService;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEvent;
import consulo.util.concurrent.coroutine.CoroutineStep;
import consulo.util.concurrent.coroutine.step.CodeExecution;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Max Medvedev
 */
public class GroovyMarkerTypes {
    static final MarkerType OVERRIDING_PROPERTY_TYPE = new MarkerType(
        "OVERRIDING_PROPERTY_TYPE",
        new Function<>() {
            @Nullable
            @Override
            public String apply(PsiElement psiElement) {
                if (!(psiElement.getParent() instanceof GrField field)) {
                    return null;
                }

                List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
                HtmlBuilder builder = new HtmlBuilder();
                int count = 0;
                boolean first = true;
                for (GrAccessorMethod method : accessors) {
                    PsiMethod[] superMethods = method.findSuperMethods(false);
                    count += superMethods.length;
                    if (superMethods.length == 0) {
                        continue;
                    }
                    PsiMethod superMethod = superMethods[0];
                    boolean isAbstract = method.isAbstract();
                    boolean isSuperAbstract = superMethod.isAbstract();

                    BiFunction<String, String, LocalizeValue> pattern =
                        isSuperAbstract && !isAbstract ? DaemonLocalize::methodImplementsIn : DaemonLocalize::methodOverridesIn;
                    if (!first) {
                        builder.append(HtmlChunk.br());
                    }
                    first = false;
                    composeText(superMethods, pattern, builder);
                }
                if (count == 0) {
                    return null;
                }
                return builder.wrapWith(HtmlChunk.body()).wrapWith(HtmlChunk.html()).toString();
            }
        },
        new LineMarkerNavigator() {
            @Override
            @RequiredUIAccess
            public void browse(ComponentEvent<?> e, PsiElement element) {
                if (!(element.getParent() instanceof GrField field)) {
                    return;
                }

                AtomicBoolean showMethodNames = new AtomicBoolean();

                Application.get().getInstance(PsiTargetNavigationService.class)
                    .<PsiMethod>newNavigator(() -> {
                        List<PsiMethod> superMethods = new ArrayList<>();
                        for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors(field)) {
                            Collections.addAll(superMethods, method.findSuperMethods(false));
                        }
                        showMethodNames.set(!PsiUtil.allMethodsHaveSameSignature(superMethods.toArray(PsiMethod.EMPTY_ARRAY)));
                        return superMethods;
                    })
                    .presentationProvider(new MethodOrFunctionalExpressionPresentationProvider(showMethodNames::get))
                    .title(DaemonLocalize.navigationTitleSuperMethod(field.getName()))
                    .findUsagesTitle(DaemonLocalize.navigationFindusagesTitleSuperMethod(field.getName()))
                    .navigate(e, field.getProject());
            }
        }
    );
    static final MarkerType OVERRIDEN_PROPERTY_TYPE = new MarkerType(
        "OVERRIDEN_PROPERTY_TYPE",
        new Function<>() {
            @Nullable
            @Override
            @RequiredReadAction
            public String apply(PsiElement element) {
                if (!(element.getParent() instanceof GrField field)) {
                    return null;
                }
                List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);

                PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor =
                    new PsiElementProcessor.CollectElementsWithLimit<>(5);

                for (GrAccessorMethod method : accessors) {
                    OverridingMethodsSearch.search(method, true).forEach(new PsiElementProcessorAdapter<>(processor));
                }
                if (processor.isOverflow()) {
                    return DaemonLocalize.methodIsOverriddenTooMany().get();
                }

                PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
                if (overridings.length == 0) {
                    return null;
                }

                Comparator<? super PsiMethod> comparator = new MethodOrFunctionalExpressionPresentationProvider(() -> false).comparator();
                Arrays.sort(overridings, comparator);

                BiFunction<String, String, LocalizeValue> pattern =
                    (methodName, className) -> LocalizeValue.of("\u00A0\u00A0\u00A0\u00A0" + className);
                String startHtml = DaemonLocalize.methodIsOverridenHeader().get();
                return GutterIconTooltipHelper.composeText(overridings, startHtml, pattern).toString();
            }
        },
        new LineMarkerNavigator() {
            @Override
            @RequiredUIAccess
            public void browse(ComponentEvent<?> e, PsiElement element) {
                if (!(element.getParent() instanceof GrField field)) {
                    return;
                }
                if (DumbService.isDumb(element.getProject())) {
                    DumbService.getInstance(element.getProject())
                        .showDumbModeNotification("Navigation to overriding classes is not possible during index update");
                    return;
                }

                AtomicBoolean showMethodNames = new AtomicBoolean();
                MethodOrFunctionalExpressionPresentationProvider provider =
                    new MethodOrFunctionalExpressionPresentationProvider(showMethodNames::get);

                int[] found = new int[1];

                CoroutineStep<Void, Collection<PsiMethod>> prefetch = CodeExecution.supply(() -> {
                    CommonProcessors.CollectProcessor<PsiMethod> collectProcessor =
                        new CommonProcessors.CollectProcessor<>(new HashSet<>());
                    for (GrAccessorMethod method : ReadAction.compute(() -> GroovyPropertyUtils.getFieldAccessors(field))) {
                        OverridingMethodsSearch.search(method, true).forEach(collectProcessor);
                    }

                    List<PsiMethod> overridings = new ArrayList<>(collectProcessor.getResults());
                    found[0] = overridings.size();
                    ReadAction.run(() -> {
                        showMethodNames.set(!PsiUtil.allMethodsHaveSameSignature(overridings.toArray(PsiMethod.EMPTY_ARRAY)));
                        overridings.sort(provider.comparator());
                    });
                    return overridings;
                });

                Application.get().getInstance(PsiTargetNavigationService.class)
                    .newNavigator(prefetch)
                    .presentationProvider(provider)
                    .title(LocalizeValue.lazy(() -> DaemonLocalize.navigationTitleOverriderMethod(field.getName(), found[0])))
                    .findUsagesTitle(LocalizeValue.localizeTODO("Overriding Methods of " + field.getName()))
                    .navigate(e, field.getProject());
            }
        }
    );
    public static final MarkerType GR_OVERRIDING_METHOD =
        new MarkerType(
            "GR_OVERRIDING_METHOD",
            element -> {
                if (!(element.getParent() instanceof GrMethod method)) {
                    return null;
                }

                Set<PsiMethod> superMethods = collectSuperMethods(method);
                if (superMethods.isEmpty()) {
                    return null;
                }

                PsiMethod superMethod = superMethods.iterator().next();
                boolean isAbstract = method.isAbstract();
                boolean isSuperAbstract = superMethod.isAbstract();

                boolean sameSignature = superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY));
                BiFunction<String, String, LocalizeValue> messagePattern;
                if (isSuperAbstract && !isAbstract) {
                    messagePattern = sameSignature ? DaemonLocalize::methodImplements : DaemonLocalize::methodImplementsIn;
                }
                else {
                    messagePattern = sameSignature ? DaemonLocalize::methodOverrides : DaemonLocalize::methodOverridesIn;
                }
                return GutterIconTooltipHelper.composeText(superMethods, "", messagePattern).toString();
            },
            new LineMarkerNavigator() {
                @Override
                @RequiredUIAccess
                public void browse(ComponentEvent<?> e, PsiElement element) {
                    if (!(element.getParent() instanceof GrMethod method)) {
                        return;
                    }

                    Application.get().getInstance(PsiTargetNavigationService.class)
                        .<NavigatablePsiElement>newNavigator(() -> new ArrayList<>(collectSuperMethods(method)))
                        .presentationProvider(new MethodOrFunctionalExpressionPresentationProvider(() -> true))
                        .title(DaemonLocalize.navigationTitleSuperMethod(method.getName()))
                        .findUsagesTitle(DaemonLocalize.navigationFindusagesTitleSuperMethod(method.getName()))
                        .navigate(e, method.getProject());
                }
            }
        );
    public static final MarkerType GR_OVERRIDEN_METHOD = new MarkerType(
        "GR_OVERRIDEN_METHOD",
        element -> {
            if (!(element.getParent() instanceof GrMethod method)) {
                return null;
            }

            final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor =
                new PsiElementProcessor.CollectElementsWithLimit<>(5);

            for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
                OverridingMethodsSearch.search(m, true).forEach(new ReadActionProcessor<>() {
                    @Override
                    @RequiredReadAction
                    public boolean processInReadAction(PsiMethod method) {
                        if (method instanceof GrTraitMethod) {
                            return true;
                        }
                        return processor.execute(method);
                    }
                });
            }

            boolean isAbstract = method.isAbstract();

            if (processor.isOverflow()) {
                return isAbstract ? DaemonLocalize.methodIsImplementedTooMany().get() : DaemonLocalize.methodIsOverriddenTooMany().get();
            }

            PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
            if (overridings.length == 0) {
                return null;
            }

            Comparator<? super PsiMethod> comparator = new MethodOrFunctionalExpressionPresentationProvider(() -> false).comparator();
            Arrays.sort(overridings, comparator);

            String startHtml =
                isAbstract ? DaemonLocalize.methodIsImplementedHeader().get() : DaemonLocalize.methodIsOverridenHeader().get();
            BiFunction<String, String, LocalizeValue> pattern =
                (methodName, className) -> LocalizeValue.of("\u00A0\u00A0\u00A0\u00A0" + className);
            return GutterIconTooltipHelper.composeText(overridings, startHtml, pattern).toString();
        },
        new LineMarkerNavigator() {
            @Override
            @RequiredUIAccess
            public void browse(ComponentEvent<?> e, PsiElement element) {
                if (!(element.getParent() instanceof GrMethod method)) {
                    return;
                }
                if (DumbService.isDumb(element.getProject())) {
                    DumbService.getInstance(element.getProject())
                        .showDumbModeNotification("Navigation to overriding classes is not possible during index update");
                    return;
                }

                AtomicBoolean showMethodNames = new AtomicBoolean();
                MethodOrFunctionalExpressionPresentationProvider provider =
                    new MethodOrFunctionalExpressionPresentationProvider(showMethodNames::get);

                CoroutineStep<Void, Collection<PsiMethod>> prefetch = CodeExecution.supply(() -> {
                    PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor =
                        new PsiElementProcessor.CollectElementsWithLimit<>(2, new HashSet<>());
                    for (GrMethod m : ReadAction.compute(() -> PsiImplUtil.getMethodOrReflectedMethods(method))) {
                        OverridingMethodsSearch.search(m, true).forEach(new ReadActionProcessor<>() {
                            @Override
                            @RequiredReadAction
                            public boolean processInReadAction(PsiMethod psiMethod) {
                                if (psiMethod instanceof GrReflectedMethod reflectedMethod) {
                                    psiMethod = reflectedMethod.getBaseMethod();
                                }
                                return collectProcessor.execute(psiMethod);
                            }
                        });
                    }

                    List<PsiMethod> overridings = new ArrayList<>(collectProcessor.getCollection());
                    ReadAction.run(() -> {
                        showMethodNames.set(!PsiUtil.allMethodsHaveSameSignature(overridings.toArray(PsiMethod.EMPTY_ARRAY)));
                        overridings.sort(provider.comparator());
                    });
                    return overridings;
                });

                Application.get().getInstance(PsiTargetNavigationService.class)
                    .newNavigator(prefetch)
                    .presentationProvider(provider)
                    .findUsagesTitle(LocalizeValue.localizeTODO("Overriding Methods of " + method.getName()))
                    .updater(new OverridingMethodsUpdater(method, provider))
                    .navigate(e, method.getProject());
            }
        }
    );

    private GroovyMarkerTypes() {
    }

    private static Set<PsiMethod> collectSuperMethods(GrMethod method) {
        Set<PsiMethod> superMethods = new HashSet<>();
        for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
            for (PsiMethod superMethod : m.findSuperMethods(false)) {
                if (superMethod instanceof GrReflectedMethod reflectedMethod) {
                    superMethod = reflectedMethod.getBaseMethod();
                }

                superMethods.add(superMethod);
            }
        }
        return superMethods;
    }

    private static HtmlBuilder composeText(
        @Nonnull PsiElement[] elements,
        BiFunction<String, String, LocalizeValue> pattern,
        HtmlBuilder result
    ) {
        Set<LocalizeValue> names = new LinkedHashSet<>();
        for (PsiElement element : elements) {
            String methodName = ((PsiMethod) element).getName();
            PsiClass aClass = ((PsiMethod) element).getContainingClass();
            String className = aClass == null ? "" : ClassPresentationUtil.getNameForClass(aClass, true);
            names.add(pattern.apply(methodName, className));
        }

        boolean first = true;
        for (LocalizeValue name : names) {
            if (!first) {
                result.append(HtmlChunk.br());
            }
            first = false;
            result.append(name);
        }
        return result;
    }

    private static class OverridingMethodsUpdater extends TargetUpdaterTask<PsiMethod> {
        private final GrMethod myMethod;

        public OverridingMethodsUpdater(GrMethod method, TargetPresentationProvider<? super PsiMethod> provider) {
            super(method.getProject(), LocalizeValue.localizeTODO(MarkerType.SEARCHING_FOR_OVERRIDING_METHODS), provider);
            myMethod = method;
        }

        @Nonnull
        @Override
        public LocalizeValue getCaption(int size) {
            return myMethod.isAbstract()
                ? DaemonLocalize.navigationTitleImplementationMethod(myMethod.getName(), size)
                : DaemonLocalize.navigationTitleOverriderMethod(myMethod.getName(), size);
        }

        @Override
        @RequiredUIAccess
        public void onSuccess() {
            super.onSuccess();
            ItemWithPresentation<PsiMethod> only = getTheOnlyOneElement();
            PsiMethod element = only == null ? null : only.dereference();
            if (element != null) {
                element.navigate(true);
                myPopup.cancel();
            }
        }

        @Override
        public void run(@Nonnull final ProgressIndicator indicator) {
            super.run(indicator);
            for (PsiMethod method : ReadAction.compute(() -> PsiImplUtil.getMethodOrReflectedMethods(myMethod))) {
                OverridingMethodsSearch.search(method, true).forEach(new CommonProcessors.CollectProcessor<PsiMethod>() {
                    @Override
                    public boolean process(PsiMethod psiMethod) {
                        PsiElement target =
                            ReadAction.compute(() -> com.intellij.java.language.impl.psi.impl.PsiImplUtil.handleMirror(psiMethod));
                        if (target instanceof PsiMethod targetMethod && !updateElement(targetMethod)) {
                            indicator.cancel();
                        }
                        indicator.checkCanceled();
                        return true;
                    }
                });
            }
        }
    }
}
