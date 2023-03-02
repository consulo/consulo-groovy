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
import com.intellij.java.impl.ide.util.MethodCellRenderer;
import com.intellij.java.indexing.search.searches.OverridingMethodsSearch;
import com.intellij.java.language.impl.psi.presentation.java.ClassPresentationUtil;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiSubstitutor;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ReadActionProcessor;
import consulo.application.util.function.CommonProcessors;
import consulo.ide.impl.idea.util.NullableFunction;
import consulo.language.editor.DaemonBundle;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.language.editor.ui.PsiElementListNavigator;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.resolve.PsiElementProcessor;
import consulo.language.psi.resolve.PsiElementProcessorAdapter;
import consulo.project.DumbService;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.text.MessageFormat;
import java.util.*;
import java.util.function.Function;

/**
 * @author Max Medvedev
 */
public class GroovyMarkerTypes {
  static final MarkerType OVERRIDING_PROPERTY_TYPE = new MarkerType("OVERRIDING_PROPERTY_TYPE", new Function<PsiElement, String>() {
    @Nullable
    @Override
    public String apply(PsiElement psiElement) {
      final PsiElement parent = psiElement.getParent();
      if (!(parent instanceof GrField)) {
        return null;
      }
      final GrField field = (GrField)parent;

      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
      StringBuilder builder = new StringBuilder();
      builder.append("<html><body>");
      int count = 0;
      String sep = "";
      for (GrAccessorMethod method : accessors) {
        PsiMethod[] superMethods = method.findSuperMethods(false);
        count += superMethods.length;
        if (superMethods.length == 0) {
          continue;
        }
        PsiMethod superMethod = superMethods[0];
        boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
        boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

        @NonNls final String key;
        if (isSuperAbstract && !isAbstract) {
          key = "method.implements.in";
        }
        else {
          key = "method.overrides.in";
        }
        builder.append(sep);
        sep = "<br>";
        composeText(superMethods, DaemonBundle.message(key), builder);
      }
      if (count == 0) {
        return null;
      }
      builder.append("</html></body>");
      return builder.toString();
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) {
        return;
      }
      final GrField field = (GrField)parent;
      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors(field);
      final ArrayList<PsiMethod> superMethods = new ArrayList<PsiMethod>();
      for (GrAccessorMethod method : accessors) {
        Collections.addAll(superMethods, method.findSuperMethods(false));
      }
      if (superMethods.isEmpty()) {
        return;
      }
      final PsiMethod[] supers = superMethods.toArray(new PsiMethod[superMethods.size()]);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(supers);
      PsiElementListNavigator.openTargets(e,
                                          supers,
                                          DaemonBundle.message("navigation.title.super.method", field.getName()),
                                          DaemonBundle.message("navigation.findUsages.title.super.method",
                                                               field.getName()),
                                          new MethodCellRenderer(showMethodNames));
    }
  }
  );
  static final MarkerType OVERRIDEN_PROPERTY_TYPE = new MarkerType("OVERRIDEN_PROPERTY_TYPE", new Function<PsiElement, String>() {
    @Nullable
    @Override
    public String apply(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) {
        return null;
      }
      final List<GrAccessorMethod> accessors = GroovyPropertyUtils.getFieldAccessors((GrField)parent);

      PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor = new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);

      for (GrAccessorMethod method : accessors) {
        OverridingMethodsSearch.search(method, true).forEach(new PsiElementProcessorAdapter<PsiMethod>(processor));
      }
      if (processor.isOverflow()) {
        return DaemonBundle.message("method.is.overridden.too.many");
      }

      PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
      if (overridings.length == 0) {
        return null;
      }

      Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
      Arrays.sort(overridings, comparator);

      String start = DaemonBundle.message("method.is.overriden.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
      return GutterIconTooltipHelper.composeText(overridings, start, pattern);
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrField)) {
        return;
      }
      if (DumbService.isDumb(element.getProject())) {
        DumbService.getInstance(element.getProject())
                   .showDumbModeNotification("Navigation to overriding classes is not possible during index update");
        return;
      }

      final GrField field = (GrField)parent;


      final CommonProcessors.CollectProcessor<PsiMethod> collectProcessor =
        new CommonProcessors.CollectProcessor<PsiMethod>(new HashSet<PsiMethod>());
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors(field)) {
                OverridingMethodsSearch.search(method, true).forEach(collectProcessor);
              }
            }
          });
        }
      }, "Searching for overriding methods", true, field.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiMethod[] overridings = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
      if (overridings.length == 0) {
        return;
      }
      String title = DaemonBundle.message("navigation.title.overrider.method", field.getName(), overridings.length);
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(overridings);
      MethodCellRenderer renderer = new MethodCellRenderer(showMethodNames);
      Arrays.sort(overridings, renderer.getComparator());
      PsiElementListNavigator.openTargets(e, overridings, title, "Overriding Methods of " + field.getName(), renderer);
    }
  }
  );
  public static final MarkerType GR_OVERRIDING_METHOD = new MarkerType("GR_OVERRIDING_METHOD", new NullableFunction<PsiElement, String>() {
    @Override
    public String apply(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrMethod)) {
        return null;
      }
      GrMethod method = (GrMethod)parent;

      Set<PsiMethod> superMethods = collectSuperMethods(method);
      if (superMethods.isEmpty()) {
        return null;
      }

      PsiMethod superMethod = superMethods.iterator().next();
      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
      boolean isSuperAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);

      final boolean sameSignature = superMethod.getSignature(PsiSubstitutor.EMPTY).equals(method.getSignature(PsiSubstitutor.EMPTY));
      @NonNls final String key;
      if (isSuperAbstract && !isAbstract) {
        key = sameSignature ? "method.implements" : "method.implements.in";
      }
      else {
        key = sameSignature ? "method.overrides" : "method.overrides.in";
      }
      return GutterIconTooltipHelper.composeText(superMethods, "", DaemonBundle.message(key));
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrMethod)) {
        return;
      }
      GrMethod method = (GrMethod)parent;

      Set<PsiMethod> superMethods = collectSuperMethods(method);
      if (superMethods.isEmpty()) {
        return;
      }
      PsiElementListNavigator.openTargets(e,
                                          superMethods.toArray(new NavigatablePsiElement[superMethods.size()]),
                                          DaemonBundle.message("navigation.title.super.method", method.getName()),
                                          DaemonBundle.message("navigation.findUsages.title.super.method", method.getName()),
                                          new MethodCellRenderer(true));

    }
  }
  );
  public static final MarkerType GR_OVERRIDEN_METHOD = new MarkerType("GR_OVERRIDEN_METHOD", new NullableFunction<PsiElement, String>() {
    @Override
    public String apply(PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrMethod)) {
        return null;
      }
      GrMethod method = (GrMethod)parent;

      final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> processor =
        new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(5);

      for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
        OverridingMethodsSearch.search(m, true).forEach(new ReadActionProcessor<PsiMethod>() {
          @Override
          public boolean processInReadAction(PsiMethod method) {
            if (method instanceof GrTraitMethod) {
              return true;
            }
            return processor.execute(method);
          }
        });
      }

      boolean isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);

      if (processor.isOverflow()) {
        return isAbstract ? DaemonBundle.message("method.is.implemented.too.many") : DaemonBundle.message("method.is.overridden.too.many");
      }

      PsiMethod[] overridings = processor.toArray(new PsiMethod[processor.getCollection().size()]);
      if (overridings.length == 0) {
        return null;
      }

      Comparator<PsiMethod> comparator = new MethodCellRenderer(false).getComparator();
      Arrays.sort(overridings, comparator);

      String start = isAbstract ? DaemonBundle.message("method.is.implemented.header") : DaemonBundle.message("method.is.overriden.header");
      @NonNls String pattern = "&nbsp;&nbsp;&nbsp;&nbsp;{1}";
      return GutterIconTooltipHelper.composeText(overridings, start, pattern);
    }
  }, new LineMarkerNavigator() {
    @Override
    public void browse(MouseEvent e, PsiElement element) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof GrMethod)) {
        return;
      }
      if (DumbService.isDumb(element.getProject())) {
        DumbService.getInstance(element.getProject())
                   .showDumbModeNotification("Navigation to overriding classes is not possible during index update");
        return;
      }


      //collect all overrings (including fields with implicit accessors and method with default parameters)
      final GrMethod method = (GrMethod)parent;
      final PsiElementProcessor.CollectElementsWithLimit<PsiMethod> collectProcessor =
        new PsiElementProcessor.CollectElementsWithLimit<PsiMethod>(2, new HashSet<PsiMethod>());
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            @Override
            public void run() {
              for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
                OverridingMethodsSearch.search(m, true).forEach(new ReadActionProcessor<PsiMethod>() {
                  @Override
                  public boolean processInReadAction(PsiMethod psiMethod) {
                    if (psiMethod instanceof GrReflectedMethod) {
                      psiMethod = ((GrReflectedMethod)psiMethod).getBaseMethod();
                    }
                    return collectProcessor.execute(psiMethod);
                  }
                });
              }
            }
          });
        }
      }, MarkerType.SEARCHING_FOR_OVERRIDING_METHODS, true, method.getProject(), (JComponent)e.getComponent())) {
        return;
      }

      PsiMethod[] overridings = collectProcessor.toArray(PsiMethod.EMPTY_ARRAY);
      if (overridings.length == 0) {
        return;
      }

      PsiElementListCellRenderer<PsiMethod> renderer = new MethodCellRenderer(!PsiUtil.allMethodsHaveSameSignature(overridings));
      Arrays.sort(overridings, renderer.getComparator());
      final OverridingMethodsUpdater methodsUpdater = new OverridingMethodsUpdater(method, renderer);
      PsiElementListNavigator.openTargets(e,
                                          overridings,
                                          methodsUpdater.getCaption(overridings.length),
                                          "Overriding Methods of " + method.getName(),
                                          renderer,
                                          methodsUpdater);

    }
  }
  );

  private GroovyMarkerTypes() {
  }

  private static Set<PsiMethod> collectSuperMethods(GrMethod method) {
    Set<PsiMethod> superMethods = new HashSet<PsiMethod>();
    for (GrMethod m : PsiImplUtil.getMethodOrReflectedMethods(method)) {
      for (PsiMethod superMethod : m.findSuperMethods(false)) {
        if (superMethod instanceof GrReflectedMethod) {
          superMethod = ((GrReflectedMethod)superMethod).getBaseMethod();
        }

        superMethods.add(superMethod);
      }
    }
    return superMethods;
  }

  private static StringBuilder composeText(@Nonnull PsiElement[] elements, final String pattern, StringBuilder result) {
    Set<String> names = new LinkedHashSet<String>();
    for (PsiElement element : elements) {
      String methodName = ((PsiMethod)element).getName();
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      String className = aClass == null ? "" : ClassPresentationUtil.getNameForClass(aClass, true);
      names.add(MessageFormat.format(pattern, methodName, className));
    }

    @NonNls String sep = "";
    for (String name : names) {
      result.append(sep);
      sep = "<br>";
      result.append(name);
    }
    return result;
  }

  private static class OverridingMethodsUpdater extends consulo.ide.impl.idea.codeInsight.navigation.ListBackgroundUpdaterTask {
    private final GrMethod myMethod;
    private final PsiElementListCellRenderer myRenderer;

    public OverridingMethodsUpdater(GrMethod method, PsiElementListCellRenderer renderer) {
      super(method.getProject(), MarkerType.SEARCHING_FOR_OVERRIDING_METHODS);
      myMethod = method;
      myRenderer = renderer;
    }

    @Override
    public String getCaption(int size) {
      return myMethod.hasModifierProperty(PsiModifier.ABSTRACT) ? DaemonBundle.message("navigation.title.implementation.method",
                                                                                       myMethod.getName(),
                                                                                       size) : DaemonBundle.message(
        "navigation.title.overrider.method",
        myMethod.getName(),
        size);
    }

    @Override
    public void run(@Nonnull final ProgressIndicator indicator) {
      super.run(indicator);
      for (PsiMethod method : PsiImplUtil.getMethodOrReflectedMethods(myMethod)) {
        OverridingMethodsSearch.search(method, true).forEach(new CommonProcessors.CollectProcessor<PsiMethod>() {
          @Override
          public boolean process(PsiMethod psiMethod) {
            if (!updateComponent(com.intellij.java.language.impl.psi.impl.PsiImplUtil.handleMirror(psiMethod),
                                 myRenderer.getComparator())) {
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
