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
package org.jetbrains.plugins.groovy.impl.codeInspection.assignment;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiClassType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Created by Max Medvedev on 05/02/14
 */
public class GrListOrMapInfo implements ConstructorCallInfo<GrListOrMap> {
  private final GrListOrMap myListOrMap;
  private final LiteralConstructorReference myReference;

  public GrListOrMapInfo(GrListOrMap listOrMap) {
    myListOrMap = listOrMap;

    assert listOrMap.getReference() instanceof LiteralConstructorReference;
    myReference = ((LiteralConstructorReference)listOrMap.getReference());
  }

  @Nullable
  @Override
  public GrArgumentList getArgumentList() {
    return null;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    if (myListOrMap.isMap()) {
      GrNamedArgument[] args = myListOrMap.getNamedArguments();
      if (args.length == 0) return new PsiType[]{myListOrMap.getType()};

      return PsiUtil.getArgumentTypes(args, GrExpression.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY, true, null, false);
    }
    else {
      GrExpression[] args = myListOrMap.getInitializers();
      return PsiUtil.getArgumentTypes(GrNamedArgument.EMPTY_ARRAY, args, GrClosableBlock.EMPTY_ARRAY, true, null, false);
    }
  }

  @Nullable
  @Override
  public GrExpression getInvokedExpression() {
    return null;
  }

  @Nullable
  @Override
  public PsiType getQualifierInstanceType() {
    return null;
  }

  @Nonnull
  @Override
  public PsiElement getHighlightElementForCategoryQualifier() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("not applicable");
  }

  @Nonnull
  @Override
  public PsiElement getElementToHighlight() {
    return myListOrMap;
  }

  @Nonnull
  @Override
  public GroovyResolveResult advancedResolve() {
    return PsiImplUtil.extractUniqueResult(multiResolve());
  }

  @Nonnull
  @Override
  public GroovyResolveResult[] multiResolve() {
    GroovyResolveResult[] results = myReference.multiResolve(false);
    if (results.length == 1 && results[0].getElement() instanceof PsiClass) {
      return GroovyResolveResult.EMPTY_ARRAY; //the same behaviour as constructor calls
    }
    return results;
  }

  @Nonnull
  @Override
  public GrListOrMap getCall() {
    return myListOrMap;
  }

  @Override
  public GroovyResolveResult[] multiResolveClass() {
    PsiClassType type = myReference.getConstructedClassType();
    if (type == null) return GroovyResolveResult.EMPTY_ARRAY;

    final GroovyResolveResult result = GroovyResolveResultImpl.from(type.resolveGenerics());
    if (result == GroovyResolveResult.EMPTY_RESULT) return GroovyResolveResult.EMPTY_ARRAY;
    return new GroovyResolveResult[]{result};
  }

  @Nonnull
  @Override
  public GrExpression[] getExpressionArguments() {
    return myListOrMap.isMap() ? GrExpression.EMPTY_ARRAY : myListOrMap.getInitializers();
  }

  @Nonnull
  @Override
  public GrClosableBlock[] getClosureArguments() {
    return GrClosableBlock.EMPTY_ARRAY;
  }

  @Nonnull
  @Override
  public GrNamedArgument[] getNamedArguments() {
    return myListOrMap.isMap() ? myListOrMap.getNamedArguments() : GrNamedArgument.EMPTY_ARRAY;
  }
}
