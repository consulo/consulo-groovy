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
package org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic;

import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiVariable;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.component.persist.PersistentStateComponent;
import consulo.project.Project;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.impl.annotator.intentions.dynamic.ui.DynamicElementSettings;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
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

  @Nullable
  public abstract String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType);

  @Nullable
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