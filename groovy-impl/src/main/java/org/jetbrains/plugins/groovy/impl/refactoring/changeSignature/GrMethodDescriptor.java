/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring.changeSignature;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.util.VisibilityUtil;
import consulo.ide.impl.idea.refactoring.changeSignature.MethodDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrMethodDescriptor implements MethodDescriptor<GrParameterInfo, String> {
  private final GrMethod myMethod;

  public GrMethodDescriptor(GrMethod method) {
    myMethod = method;
  }

  @Override
  public String getName() {
    return myMethod.getName();
  }

  @Override
  public List<GrParameterInfo> getParameters() {
    final ArrayList<GrParameterInfo> result = new ArrayList<GrParameterInfo>();
    final GrParameter[] parameters = myMethod.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      GrParameter parameter = parameters[i];
      GrExpression initializer = parameter.getInitializerGroovy();
      String initializerText = initializer != null ? initializer.getText() : null;
      GrParameterInfo info = new GrParameterInfo(parameter.getName(), "", initializerText, parameter.getType(), i, false);
      result.add(info);
    }
    return result;
  }

  @Override
  public int getParametersCount() {
    return myMethod.getParameterList().getParametersCount();
  }

  @Override
  public String getVisibility() {
    return VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
  }

  @Override
  public GrMethod getMethod() {
    return myMethod;
  }

  @Override
  public boolean canChangeVisibility() {
    PsiClass containingClass = myMethod.getContainingClass();
    return containingClass != null && !containingClass.isInterface();
  }

  @Override
  public boolean canChangeParameters() {
    return true;
  }

  @Override
  public boolean canChangeName() {
    return !myMethod.isConstructor();
  }

  @Override
  public ReadWriteOption canChangeReturnType() {
    return myMethod.isConstructor() ? ReadWriteOption.None : ReadWriteOption.ReadWrite;
  }

  public String getReturnTypeText() {
    GrTypeElement returnTypeElement = myMethod.getReturnTypeElementGroovy();
    return returnTypeElement != null ? returnTypeElement.getText() : "";
  }
}
