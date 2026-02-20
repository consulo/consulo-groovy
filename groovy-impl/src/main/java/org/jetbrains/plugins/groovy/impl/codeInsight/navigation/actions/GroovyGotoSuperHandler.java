/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.codeInsight.navigation.actions;

import com.intellij.java.language.psi.CommonClassNames;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.util.PsiSuperMethodUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.codeInsight.navigation.GotoTargetHandler;
import consulo.ide.impl.idea.codeInsight.navigation.actions.GotoSuperAction;
import consulo.language.Language;
import consulo.language.editor.CodeInsightBundle;
import consulo.language.editor.action.GotoSuperActionHander;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author Medvedev Max
 */
@ExtensionImpl
public class GroovyGotoSuperHandler extends GotoTargetHandler implements GotoSuperActionHander {

  private static final Logger LOG = Logger.getInstance(GroovyGotoSuperHandler.class);

  @Override
  protected String getFeatureUsedKey() {
    return GotoSuperAction.FEATURE_ID;
  }

  @Override
  protected GotoData getSourceAndTargetElements(Editor editor, PsiFile file) {
    PsiMember e = findSource(editor, file);
    if (e == null) return null;
    return new GotoData(e, findTargets(e), Collections.<AdditionalAction>emptyList());
  }

  @Override
  protected String getChooserTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.super.method.chooser.title");
  }

  @Override
  protected String getFindUsagesTitle(PsiElement sourceElement, String name, int length) {
    return CodeInsightBundle.message("goto.super.method.findUsages.title", name);
  }

  @Override
  protected String getNotFoundMessage(Project project, Editor editor, PsiFile file) {
    PsiMember source = findSource(editor, file);
    if (source instanceof PsiClass) {
      return GroovyBundle.message("no.super.classes.found");
    }
    else if (source instanceof PsiMethod || source instanceof GrField) {
      return GroovyBundle.message("no.super.method.found");
    }
    else {
      throw new IncorrectOperationException("incorrect element is found: " + (source == null ? "null" : source.getClass()
                                                                                                              .getCanonicalName()));
    }
  }

  @Nullable
  private static PsiMember findSource(Editor editor, PsiFile file) {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, GrField.class, PsiClass.class);
  }

  private static PsiElement[] findTargets(PsiMember e) {
    if (e instanceof PsiClass) {
      PsiClass aClass = (PsiClass)e;
      List<PsiClass> allSupers = new ArrayList<PsiClass>(Arrays.asList(aClass.getSupers()));
      for (Iterator<PsiClass> iterator = allSupers.iterator(); iterator.hasNext(); ) {
        PsiClass superClass = iterator.next();
        if (CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) iterator.remove();
      }
      return allSupers.toArray(new PsiClass[allSupers.size()]);
    }
    else if (e instanceof PsiMethod) {
      return getSupers((PsiMethod)e);
    }
    else {
      LOG.assertTrue(e instanceof GrField);
      List<PsiMethod> supers = new ArrayList<PsiMethod>();
      for (GrAccessorMethod method : GroovyPropertyUtils.getFieldAccessors((GrField)e)) {
        supers.addAll(Arrays.asList(getSupers(method)));
      }
      return supers.toArray(new PsiMethod[supers.size()]);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nonnull
  private static PsiMethod[] getSupers(PsiMethod method) {
    if (method.isConstructor()) {
      PsiMethod constructorInSuper = PsiSuperMethodUtil.findConstructorInSuper(method);
      if (constructorInSuper != null) {
        return new PsiMethod[]{constructorInSuper};
      }
    }
    else {
      return method.findSuperMethods(false);
    }

    return PsiMethod.EMPTY_ARRAY;
  }

  @Override
  public boolean isValidFor(Editor editor, PsiFile file) {
    return file != null && GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
