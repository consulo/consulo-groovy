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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.codehaus.groovy.runtime.GroovyCategorySupport;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
* @author peter
*/
public class GroovyClassDescriptor {
  static {
    try {
      final AtomicInteger integer = GroovyCategorySupport.getCategoryNameUsage("aaa");
    }
    catch (NoSuchMethodError e) {
      throw new RuntimeException("Incompatible Groovy jar in classpath: " + GroovyCategorySupport.class.getResource("/") + ", please remove it");
    }
  }

  private final PsiType myPsiType;
  private final PsiElement myPlace;
  private final PsiFile myFile;
  private final String myTypeText;

  @SuppressWarnings({"SetReplaceableByEnumSet"}) //order is important
  final Set<Factor> affectingFactors = new LinkedHashSet<Factor>();

  public GroovyClassDescriptor(@Nonnull PsiType psiType, PsiElement place, final PsiFile placeFile) {
    myPsiType = psiType;
    myPlace = place;
    myFile = placeFile;
    myTypeText = myPsiType.getCanonicalText();
  }

  public Project getProject() {
    return myFile.getProject();
  }

  public GlobalSearchScope getResolveScope() {
    //affectingFactors.add(Factor.placeFile);
    return myPlace.getResolveScope();
  }

  @Nullable
  public String getTypeText() {
    affectingFactors.add(Factor.qualifierType);
    return myTypeText;
  }

  public boolean isInheritor(String qname) {
    return InheritanceUtil.isInheritor(getPsiType(), qname);
  }

  public PsiElement getPlace() {
    affectingFactors.add(Factor.placeElement);
    return myPlace;
  }

  @Nonnull
  public PsiType getPsiType() {
    affectingFactors.add(Factor.qualifierType);
    return myPsiType;
  }

  public PsiFile getPlaceFile() {
    affectingFactors.add(Factor.placeFile);
    return myFile;
  }

  public PsiFile justGetPlaceFile() {
    return myFile;
  }

}
