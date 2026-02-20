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
package org.jetbrains.plugins.groovy.impl.editor;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.editor.refactoring.ImportOptimizer;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.primitive.objects.ObjectIntMap;
import consulo.util.collection.primitive.objects.ObjectMaps;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.impl.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import jakarta.annotation.Nonnull;
import java.util.*;
import java.util.function.ObjIntConsumer;

/**
 * @author ven
 */
@ExtensionImpl
public class GroovyImportOptimizer implements ImportOptimizer {
  public static Comparator<GrImportStatement> getComparator(final GroovyCodeStyleSettings settings) {
    return new Comparator<GrImportStatement>() {
      @Override
      public int compare(GrImportStatement statement1, GrImportStatement statement2) {
        if (settings.LAYOUT_STATIC_IMPORTS_SEPARATELY) {
          if (statement1.isStatic() && !statement2.isStatic()) {
            return 1;
          }
          if (statement2.isStatic() && !statement1.isStatic()) {
            return -1;
          }
        }

        GrCodeReferenceElement ref1 = statement1.getImportReference();
        GrCodeReferenceElement ref2 = statement2.getImportReference();
        String name1 = ref1 != null ? PsiUtil.getQualifiedReferenceText(ref1) : null;
        String name2 = ref2 != null ? PsiUtil.getQualifiedReferenceText(ref2) : null;
        if (name1 == null) {
          return name2 == null ? 0 : -1;
        }
        if (name2 == null) {
          return 1;
        }
        return name1.compareTo(name2);
      }
    };
  }

  @Override
  @Nonnull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file, false);
  }

  @Override
  public boolean supports(PsiFile file) {
    return file instanceof GroovyFile;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  private class MyProcessor implements Runnable {
    private final PsiFile myFile;
    private final boolean myRemoveUnusedOnly;

    private MyProcessor(PsiFile file, boolean removeUnusedOnly) {
      myFile = file;
      myRemoveUnusedOnly = removeUnusedOnly;
    }

    @Override
    public void run() {
      if (!(myFile instanceof GroovyFile)) {
        return;
      }

      GroovyFile file = ((GroovyFile)myFile);
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(file.getProject());
      Document document = documentManager.getDocument(file);
      if (document != null) {
        documentManager.commitDocument(document);
      }
      Set<String> simplyImportedClasses = new LinkedHashSet<String>();
      Set<String> staticallyImportedMembers = new LinkedHashSet<String>();
      Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
      Set<GrImportStatement> unresolvedOnDemandImports = new HashSet<GrImportStatement>();
      Set<String> implicitlyImportedClasses = new LinkedHashSet<String>();
      Set<String> innerClasses = new HashSet<String>();
      Map<String, String> aliasImported = new HashMap<>();
      Map<String, String> annotatedImports = new HashMap<>();

      GroovyImportUtil.processFile(myFile, simplyImportedClasses, staticallyImportedMembers, usedImports,
                                   unresolvedOnDemandImports, implicitlyImportedClasses, innerClasses, aliasImported,
                                   annotatedImports);
      List<GrImportStatement> oldImports = PsiUtil.getValidImportStatements(file);
      if (myRemoveUnusedOnly) {
        for (GrImportStatement oldImport : oldImports) {
          if (!usedImports.contains(oldImport)) {
            file.removeImport(oldImport);
          }
        }
        return;
      }

      // Add new import statements
      GrImportStatement[] newImports = prepare(usedImports, simplyImportedClasses, staticallyImportedMembers,
                                               implicitlyImportedClasses, innerClasses, aliasImported, annotatedImports,
                                               unresolvedOnDemandImports);
      if (oldImports.isEmpty() && newImports.length == 0 && aliasImported.isEmpty()) {
        return;
      }

      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(file.getProject());

      GroovyFile tempFile = factory.createGroovyFile("", false, null);

      for (GrImportStatement newImport : newImports) {
        tempFile.addImport(newImport);
      }

      if (!oldImports.isEmpty()) {
        int startOffset = oldImports.get(0).getTextRange().getStartOffset();
        int endOffset = oldImports.get(oldImports.size() - 1).getTextRange().getEndOffset();
        String oldText = oldImports.isEmpty() ? "" : myFile.getText().substring(startOffset, endOffset);
        if (tempFile.getText().trim().equals(oldText)) {
          return;
        }
      }

      for (GrImportStatement statement : tempFile.getImportStatements()) {
        file.addImport(statement);
      }

      for (GrImportStatement importStatement : oldImports) {
        file.removeImport(importStatement);
      }
    }

    private GrImportStatement[] prepare(Set<GrImportStatement> usedImports,
                                        Set<String> importedClasses,
                                        Set<String> staticallyImportedMembers,
                                        Set<String> implicitlyImported,
                                        Set<String> innerClasses,
                                        Map<String, String> aliased,
                                        final Map<String, String> annotations,
                                        Set<GrImportStatement> unresolvedOnDemandImports) {
      Project project = myFile.getProject();
      final GroovyCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings
        (GroovyCodeStyleSettings.class);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      ObjectIntMap<String> packageCountMap = ObjectMaps.newObjectIntHashMap();
      ObjectIntMap<String> classCountMap = ObjectMaps.newObjectIntHashMap();

      //init packageCountMap
      for (String importedClass : importedClasses) {
        if (implicitlyImported.contains(importedClass) ||
          innerClasses.contains(importedClass) ||
          aliased.containsKey(importedClass) ||
          annotations.containsKey(importedClass)) {
          continue;
        }

        String packageName = StringUtil.getPackageName(importedClass);

        if (!packageCountMap.containsKey(packageName)) {
          packageCountMap.putInt(packageName, 1);
        }
        else {
          packageCountMap.putInt(packageName, packageCountMap.getInt(packageName) + 1);
        }
      }

      //init classCountMap
      for (String importedMember : staticallyImportedMembers) {
        if (aliased.containsKey(importedMember) || annotations.containsKey(importedMember)) {
          continue;
        }

        String className = StringUtil.getPackageName(importedMember);

        if (!classCountMap.containsKey(className)) {
          classCountMap.putInt(className, 1);
        }
        else {
          classCountMap.putInt(className, classCountMap.getInt(className) + 1);
        }
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<String>();
      final List<GrImportStatement> result = new ArrayList<GrImportStatement>();

      packageCountMap.forEach(new ObjIntConsumer<String>() {
        @Override
        public void accept(String s, int i) {
          if (i >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND || settings.PACKAGES_TO_USE_IMPORT_ON_DEMAND
            .contains(s)) {
            GrImportStatement imp = factory.createImportStatementFromText(s, false, true, null);
            String annos = annotations.remove(s + ".*");
            if (annos != null) {
              imp.getAnnotationList().replace(factory.createModifierList(annos));
            }
            result.add(imp);
            PsiJavaPackage aPackage = JavaPsiFacade.getInstance(myFile.getProject()).findPackage(s);
            if (aPackage != null) {
              for (PsiClass clazz : aPackage.getClasses(myFile.getResolveScope())) {
                onDemandImportedSimpleClassNames.add(clazz.getName());
              }
            }
          }
        }
      });

      classCountMap.forEach(new ObjIntConsumer<String>() {
        @Override
        public void accept(String s, int i) {
          if (i >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            GrImportStatement imp = factory.createImportStatementFromText(s, true, true, null);
            String annos = annotations.remove(s + ".*");
            if (annos != null) {
              imp.getAnnotationList().replace(factory.createModifierList(annos));
            }
            result.add(imp);
          }
        }
      });

      List<GrImportStatement> explicated = ContainerUtil.newArrayList();
      for (String importedClass : importedClasses) {
        String parentName = StringUtil.getPackageName(importedClass);
        if (!annotations.containsKey(importedClass) && !aliased.containsKey(importedClass)) {
          if (packageCountMap.getInt(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND || settings
            .PACKAGES_TO_USE_IMPORT_ON_DEMAND.contains(parentName)) {
            continue;
          }
          if (implicitlyImported.contains(importedClass) && !onDemandImportedSimpleClassNames.contains
            (StringUtil.getShortName(importedClass))) {
            continue;
          }
        }

        GrImportStatement imp = factory.createImportStatementFromText(importedClass, false, false, null);
        String annos = annotations.remove(importedClass);
        if (annos != null) {
          imp.getAnnotationList().replace(factory.createModifierList(annos));
        }
        explicated.add(imp);
      }

      for (String importedMember : staticallyImportedMembers) {
        String className = StringUtil.getPackageName(importedMember);
        if (!annotations.containsKey(importedMember) && !aliased.containsKey(importedMember)) {
          if (classCountMap.getInt(className) >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            continue;
          }
        }
        result.add(factory.createImportStatementFromText(importedMember, true, false, null));
      }

      for (GrImportStatement anImport : usedImports) {
        if (anImport.isAliasedImport() || GroovyImportUtil.isAnnotatedImport(anImport)) {
          if (GroovyImportUtil.isAnnotatedImport(anImport)) {
            annotations.remove(GroovyImportUtil.getImportReferenceText(anImport));
          }

          if (anImport.isStatic()) {
            result.add(anImport);
          }
          else {
            explicated.add(anImport);
          }
        }
      }

      Comparator<GrImportStatement> comparator = getComparator(settings);
      Collections.sort(result, comparator);
      Collections.sort(explicated, comparator);

      explicated.addAll(result);

      if (!annotations.isEmpty()) {
        StringBuilder allSkippedAnnotations = new StringBuilder();
        for (String anno : annotations.values()) {
          allSkippedAnnotations.append(anno).append(' ');
        }
        if (explicated.isEmpty()) {
          explicated.add(factory.createImportStatementFromText(CommonClassNames.JAVA_LANG_OBJECT, false,
                                                               false, null));
        }

        GrImportStatement first = explicated.get(0);

        allSkippedAnnotations.append(first.getAnnotationList().getText());
        first.getAnnotationList().replace(factory.createModifierList(allSkippedAnnotations));
      }

      for (GrImportStatement anImport : unresolvedOnDemandImports) {
        explicated.add(anImport);
      }

      return explicated.toArray(new GrImportStatement[explicated.size()]);
    }
  }
}
