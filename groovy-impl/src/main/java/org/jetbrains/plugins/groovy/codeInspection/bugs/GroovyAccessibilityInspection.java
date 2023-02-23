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
package org.jetbrains.plugins.groovy.codeInspection.bugs;

import consulo.language.editor.inspection.scheme.InspectionProfile;
import consulo.language.editor.inspection.scheme.InspectionProjectProfileManager;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GroovyAccessibilityInspection extends GroovySuppressableInspectionTool {
  private static final String SHORT_NAME = "GroovyAccessibility";

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return BaseInspection.PROBABLE_BUGS;
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return GroovyInspectionBundle.message("access.to.inaccessible.element");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  public static boolean isInspectionEnabled(GroovyFileBase file, Project project) {
    return getInspectionProfile(project).isToolEnabled(findDisplayKey(), file);
  }

  public static GroovyAccessibilityInspection getInstance(GroovyFileBase file, Project project) {
    return (GroovyAccessibilityInspection)getInspectionProfile(project).getUnwrappedTool(SHORT_NAME, file);
  }

  public static HighlightDisplayKey findDisplayKey() {
    return HighlightDisplayKey.find(SHORT_NAME);
  }

  public static HighlightDisplayLevel getHighlightDisplayLevel(Project project, GrReferenceElement ref) {
    return getInspectionProfile(project).getErrorLevel(findDisplayKey(), ref);
  }

  @Nonnull
  private static InspectionProfile getInspectionProfile(@Nonnull Project project) {
    return InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
  }

  public static boolean isSuppressed(PsiElement ref) {
    return isElementToolSuppressedIn(ref, SHORT_NAME);
  }

}
