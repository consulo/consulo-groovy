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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce;

import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.language.psi.PsiDirectory;
import consulo.language.editor.refactoring.ui.ConflictsDialog;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyRefactoringUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

/**
 * @author Maxim.Medvedev
 */
public class GrIntroduceValidatorEngine implements GrIntroduceHandlerBase.Validator {
  private final GrIntroduceContext myContext;
  private final ConflictReporter myReporter;

  public GrIntroduceValidatorEngine(GrIntroduceContext context, ConflictReporter reporter) {
    myContext = context;
    myReporter = reporter;
  }

  public boolean isOK(GrIntroduceDialog dialog) {
    final GrIntroduceSettings settings = dialog.getSettings();
    if (settings == null) return false;
    String varName = settings.getName();
    boolean allOccurrences = settings.replaceAllOccurrences();
    final MultiMap<PsiElement, String> conflicts = isOKImpl(varName, allOccurrences);
    return conflicts.size() <= 0 || reportConflicts(conflicts, getProject());
  }

  private static boolean reportConflicts(final MultiMap<PsiElement, String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }

  private MultiMap<PsiElement, String> isOKImpl(String varName, boolean replaceAllOccurrences) {
    PsiElement firstOccurence;
    if (replaceAllOccurrences) {
      if (myContext.getOccurrences().length > 0) {
        GroovyRefactoringUtil.sortOccurrences(myContext.getOccurrences());
        firstOccurence = myContext.getOccurrences()[0];
      }
      else {
        firstOccurence = myContext.getPlace();
      }
    }
    else {
      firstOccurence = myContext.getExpression();
    }
    final MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();
    assert varName != null;

    final int offset = firstOccurence.getTextRange().getStartOffset();
    validateOccurrencesDown(myContext.getScope(), conflicts, varName, offset);
    if (!(myContext.getScope() instanceof GroovyFileBase)) {
      validateVariableOccurrencesUp(myContext.getScope(), conflicts, varName, offset);
    }
    return conflicts;
  }


  /**
   * Use for validator tests
   */
  public String isOKTest(String varName, boolean allOccurences) {
    MultiMap<PsiElement, String> list = isOKImpl(varName, allOccurences);
    String result = "";
    final String[] strings = ArrayUtil.toStringArray((Collection<String>)list.values());
    Arrays.sort(strings, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return o1.compareTo(o2);
      }
    });

    for (String s : strings) {
      result = result + s.replaceAll("<b><code>", "").replaceAll("</code></b>", "") + "\n";
    }
    if (list.size() > 0) {
      result = result.substring(0, result.length() - 1);
    }
    if (result.length() == 0) {
      result = "ok";
    }
    return result;
  }

  /**
   * @param startElement Container to start checking conflicts from
   * @param conflicts    Conflict accumulator
   * @param varName      Variable name
   * @param startOffset
   */
  private void validateOccurrencesDown(PsiElement startElement,
                                       MultiMap<PsiElement, String> conflicts,
                                       @Nonnull String varName,
                                       double startOffset) {
    PsiElement child = startElement.getFirstChild();
    while (child != null) {
      // Do not check defined classes, methods, closures and blocks before
      if (child instanceof GrTypeDefinition ||
          child instanceof GrMethod ||
          GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(child) &&
          child.getTextRange().getEndOffset() < startOffset) {
        myReporter.check(child, conflicts, varName);
        child = child.getNextSibling();
        continue;
      }
      if (child instanceof GrVariable) {
        myReporter.check(child, conflicts, varName);
        validateOccurrencesDown(child, conflicts, varName, startOffset);
      }
      else {
        validateOccurrencesDown(child, conflicts, varName, startOffset);
      }
      child = child.getNextSibling();
    }
  }

  private void validateVariableOccurrencesUp(PsiElement startElement,
                                             MultiMap<PsiElement, String> conflicts,
                                             @Nonnull String varName,
                                             double startOffset) {
    PsiElement prevSibling = startElement.getPrevSibling();
    while (prevSibling != null) {
      if (!(GroovyRefactoringUtil.isAppropriateContainerForIntroduceVariable(prevSibling) &&
            prevSibling.getTextRange().getEndOffset() < startOffset)) {
        validateOccurrencesDown(prevSibling, conflicts, varName, startOffset);
      }
      prevSibling = prevSibling.getPrevSibling();
    }

    PsiElement parent = startElement.getParent();
    // Do not check context out of method, type definition and directories
    if (parent == null ||
        parent instanceof GrMethod ||
        parent instanceof GrTypeDefinition ||
        parent instanceof GroovyFileBase ||
        parent instanceof PsiDirectory) {
      return;
    }

    validateVariableOccurrencesUp(parent, conflicts, varName, startOffset);
  }

  /**
   * Validates name to be suggested in context
   */
  public String validateName(String name, boolean increaseNumber) {
    String result = name;
    if (isOKImpl(name, true).size() > 0 && !increaseNumber || name.length() == 0) {
      return "";
    }
    int i = 1;
    while (isOKImpl(result, true).size() > 0) {
      result = name + i;
      i++;
    }
    return result;
  }

  public Project getProject() {
    return myContext.getProject();
  }
}
