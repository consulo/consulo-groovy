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
package org.jetbrains.plugins.groovy.impl.refactoring.introduce.parameter;

import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractInfoHelper;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.impl.refactoring.extract.InitialInfo;

/**
 * @author Max Medvedev
 */
public class IntroduceParameterInfoImpl extends ExtractInfoHelperBase implements IntroduceParameterInfo, ExtractInfoHelper {
  private final GrParameterListOwner myOwner;
  private final PsiElement myToSearchFor;

  public IntroduceParameterInfoImpl(InitialInfo info, GrParameterListOwner owner, PsiElement toSearchFor) {
    super(info);
    myOwner = owner;
    myToSearchFor = toSearchFor;
  }

  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getToSearchFor() {
    return myToSearchFor;
  }

  @Override
  public GrParameterListOwner getToReplaceIn() {
    return myOwner;
  }
}
