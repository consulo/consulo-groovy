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
package org.jetbrains.plugins.groovy.impl.refactoring.move;

import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassToInnerHandler;
import com.intellij.java.impl.refactoring.move.moveClassesOrPackages.MoveClassToInnerProcessor;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.javadoc.PsiDocComment;
import com.intellij.java.language.psi.util.PsiUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.usage.NonCodeUsageInfo;
import consulo.usage.UsageInfo;
import org.jetbrains.plugins.groovy.impl.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.impl.lang.psi.util.GroovyImportUtil;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyChangeContextUtil;

import jakarta.annotation.Nonnull;
import java.util.*;

/**
 * @author Max Medvedev
 */
@ExtensionImpl
public class GroovyMoveClassToInnerHandler implements MoveClassToInnerHandler {
  private static final Logger LOG = Logger.getInstance(GroovyMoveClassToInnerHandler.class);

  @Override
  public PsiClass moveClass(@Nonnull PsiClass aClass, @Nonnull PsiClass targetClass) {
    if (!(aClass instanceof GrTypeDefinition)) {
      return null;
    }

    GroovyChangeContextUtil.encodeContextInfo(aClass);


    PsiDocComment doc = aClass.getDocComment();

    PsiElement brace = targetClass.getRBrace();
    PsiClass newClass = (PsiClass)targetClass.addBefore(aClass, brace);
    PsiElement sibling = newClass.getPrevSibling();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(targetClass.getProject());
    if (!org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isNewLine(sibling)) {
      targetClass.addBefore(factory.createLineTerminator("\n "), newClass);
    }
    else if (doc != null) {
      LOG.assertTrue(sibling != null);
      sibling.replace(factory.createLineTerminator(sibling.getText() + " "));
    }

    if (doc != null) {
      targetClass.addBefore(doc, newClass);
      targetClass.addBefore(factory.createLineTerminator("\n"), newClass);
    }

    if (targetClass.isInterface()) {
      PsiUtil.setModifierProperty(newClass, PsiModifier.PUBLIC, true);
    }
    else {
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, true);
    }
    GroovyChangeContextUtil.decodeContextInfo(newClass, null, null);

    return newClass;
  }


  @Override
  public List<PsiElement> filterImports(@Nonnull List<UsageInfo> usageInfos, @Nonnull Project project) {
    final List<PsiElement> importStatements = new ArrayList<PsiElement>();
    if (!CodeStyleSettingsManager.getSettings(project).getCustomSettings(GroovyCodeStyleSettings.class)
      .INSERT_INNER_CLASS_IMPORTS) {
      filterUsagesInImportStatements(usageInfos, importStatements);
    }
    else {
      //rebind imports first
      Collections.sort(usageInfos, new Comparator<UsageInfo>() {
        @Override
        public int compare(UsageInfo o1, UsageInfo o2) {
          return PsiUtil.BY_POSITION.compare(o1.getElement(), o2.getElement());
        }
      });
    }
    return importStatements;
  }

  private static void filterUsagesInImportStatements(final List<UsageInfo> usages,
                                                     final List<PsiElement> importStatements) {
    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext(); ) {
      UsageInfo usage = iterator.next();
      PsiElement element = usage.getElement();
      if (element == null) {
        continue;
      }
      GrImportStatement stmt = PsiTreeUtil.getParentOfType(element, GrImportStatement.class);
      if (stmt != null) {
        importStatements.add(stmt);
        iterator.remove();
      }
    }
  }

  @Override
  public void retargetClassRefsInMoved(@Nonnull final Map<PsiElement, PsiElement> oldToNewElementsMapping) {
    for (final PsiElement newClass : oldToNewElementsMapping.values()) {
      if (!(newClass instanceof GrTypeDefinition)) {
        continue;
      }
      ((GrTypeDefinition)newClass).accept(new GroovyRecursiveElementVisitor() {
        @Override
        public void visitReferenceExpression(GrReferenceExpression reference) {
          if (visitRef(reference)) {
            return;
          }
          super.visitReferenceExpression(reference);
        }

        @Override
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          visitRef(refElement);
          super.visitCodeReferenceElement(refElement);
        }

        private boolean visitRef(GrReferenceElement reference) {
          PsiElement element = reference.resolve();
          if (element instanceof PsiClass) {
            for (PsiElement oldClass : oldToNewElementsMapping.keySet()) {
              if (PsiTreeUtil.isAncestor(oldClass, element, false)) {
                PsiClass newInnerClass = findMatchingClass((PsiClass)oldClass,
                                                           (PsiClass)oldToNewElementsMapping.get(oldClass), (PsiClass)element);
                try {
                  reference.bindToElement(newInnerClass);
                  return true;
                }
                catch (IncorrectOperationException ex) {
                  LOG.error(ex);
                }
              }
            }
          }
          return false;
        }
      });
    }
  }


  private static PsiClass findMatchingClass(final PsiClass classToMove,
                                            final PsiClass newClass,
                                            final PsiClass innerClass) {
    if (classToMove == innerClass) {
      return newClass;
    }
    PsiClass parentClass = findMatchingClass(classToMove, newClass, innerClass.getContainingClass());
    PsiClass newInnerClass = parentClass.findInnerClassByName(innerClass.getName(), false);
    assert newInnerClass != null;
    return newInnerClass;
  }

  @Override
  public void retargetNonCodeUsages(@Nonnull final Map<PsiElement, PsiElement> oldToNewElementMap,
                                    @Nonnull final NonCodeUsageInfo[] nonCodeUsages) {
    for (PsiElement newClass : oldToNewElementMap.values()) {
      if (!(newClass instanceof GrTypeDefinition)) {
        continue;
      }

      newClass.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(final PsiElement element) {
          super.visitElement(element);
          List<NonCodeUsageInfo> list = element.getCopyableUserData(MoveClassToInnerProcessor
                                                                      .ourNonCodeUsageKey);
          if (list != null) {
            for (NonCodeUsageInfo info : list) {
              for (int i = 0; i < nonCodeUsages.length; i++) {
                if (nonCodeUsages[i] == info) {
                  nonCodeUsages[i] = info.replaceElement(element);
                  break;
                }
              }
            }
            element.putCopyableUserData(MoveClassToInnerProcessor.ourNonCodeUsageKey, null);
          }
        }
      });
    }
  }

  @Override
  public void removeRedundantImports(PsiFile targetClassFile) {
    if (targetClassFile instanceof GroovyFile) {
      GroovyFile file = (GroovyFile)targetClassFile;
      final Set<GrImportStatement> usedImports = GroovyImportUtil.findUsedImports(file);
      final List<GrImportStatement> validImports = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
        .getValidImportStatements(file);
      for (GrImportStatement importStatement : validImports) {
        if (!usedImports.contains(importStatement)) {
          file.removeImport(importStatement);
        }
      }
    }
  }
}
