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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public abstract class DynamicManager implements PersistentStateComponent<DRootElement> {

  @Nonnull
  public static DynamicManager getInstance(@Nonnull Project project) {
    return project.getComponent(DynamicManager.class);
  }

  /**
   * ************* Properties and methods *************
   */

  /*
  * Fire changes
  */
  public abstract void fireChange();

  /*
  * Returns root element
  */

  public abstract DRootElement getRootElement();

  /*
  * Returns all containing classes
  */

  @Nonnull
  public abstract Collection<DClassElement> getAllContainingClasses();

  public abstract void replaceClassName(@Nullable final DClassElement oldClassElement, String newClassName);

  public abstract void addProperty(DynamicElementSettings settings);

  public abstract void addMethod(DynamicElementSettings settings);

  public abstract void removeClassElement(DClassElement classElement);

  @Nullable
  public abstract DPropertyElement findConcreteDynamicProperty(final String containingClassName, final String propertyName);

  @Nonnull
  public abstract Collection<DPropertyElement> findDynamicPropertiesOfClass(final String containingClassName);

  @Nullable
  public abstract String getPropertyType(String className, String propertyName);

  @Nullable
  public abstract String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName);

  @javax.annotation.Nullable
  public abstract String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType);

  @javax.annotation.Nullable
  public abstract DMethodElement findConcreteDynamicMethod(final String containingClassName, final String name, final String[] types);

  public abstract void removeItemElement(DItemElement element);

  public abstract void replaceDynamicMethodType(String className, String name, List<ParamInfo> myPairList, String oldType, String newType);

  @Nonnull
  public abstract DClassElement getOrCreateClassElement(Project project, String className);

  public abstract DClassElement getClassElementByItem(DItemElement itemElement);

  public abstract void replaceDynamicMethodName(String className, String oldName, String newName, String[] types);

  public abstract Iterable<PsiMethod> getMethods(String classQname);

  public abstract Iterable<PsiVariable> getProperties(String classQname);

  public abstract DItemElement createDynamicElement(DynamicElementSettings settings);
}