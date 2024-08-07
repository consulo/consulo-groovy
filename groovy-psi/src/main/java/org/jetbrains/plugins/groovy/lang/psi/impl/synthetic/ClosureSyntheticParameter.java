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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.java.language.psi.PsiIntersectionType;
import com.intellij.java.language.psi.PsiType;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.NavigationItem;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.typeEnhancers.GrVariableEnhancer;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.function.Function;

/**
 * @author ven
 */
public class ClosureSyntheticParameter extends GrLightParameter implements NavigationItem, GrRenameableLightElement {
  private static final Function<ClosureSyntheticParameter, PsiType> TYPES_CALCULATOR = parameter -> {
    PsiType typeGroovy = GrVariableEnhancer.getEnhancedType(parameter);
    if (typeGroovy instanceof PsiIntersectionType) {
      return ((PsiIntersectionType)typeGroovy).getRepresentative();
    }
    return typeGroovy;
  };

  private final GrClosableBlock myClosure;

  public ClosureSyntheticParameter(GrClosableBlock closure) {
    super(GrClosableBlock.IT_PARAMETER_NAME, TypesUtil.getJavaLangObject(closure), closure);
    myClosure = closure;
    setOptional(true);
  }

  @Override
  public PsiElement setName(@Nonnull String newName) throws IncorrectOperationException {
    if (!newName.equals(getName())) {
      GrParameter parameter = GroovyPsiElementFactory.getInstance(getProject()).createParameter(newName, (String)null, null);
      myClosure.addParameter(parameter);
    }
    return this;
  }

  @Override
  @Nullable
  public PsiType getTypeGroovy() {
    assert isValid();

    return TypeInferenceHelper.getCurrentContext().getExpressionType(this, TYPES_CALCULATOR);
  }

  @Override
  @Nullable
  public PsiType getDeclaredType() {
    return null;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  @Nonnull
  public SearchScope getUseScope() {
    return new LocalSearchScope(myClosure);
  }

  public GrClosableBlock getClosure() {
    return myClosure;
  }

  @Override
  public GrExpression getInitializerGroovy() {
    return GroovyPsiElementFactory.getInstance(getProject()).createExpressionFromText("null");
  }

  @Nullable
  @Override
  public PsiElement getEllipsisDots() {
    return null;
  }
}
