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
package org.jetbrains.plugins.groovy.impl.refactoring.convertToJava;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnonymousClassType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import jakarta.annotation.Nonnull;

/**
 * @author Medvedev Max
 */
public class TypeWriter extends PsiTypeVisitor<Object> {

  private final boolean acceptEllipsis;
  private final StringBuilder builder;
  private final ClassNameProvider classNameProvider;
  private final PsiElement context;

  public static void writeTypeForNew(@Nonnull StringBuilder builder, @Nullable PsiType type, @Nonnull PsiElement context) {

    //new Array[] cannot contain generics
    if (type instanceof PsiArrayType) {
      PsiType erased = TypeConversionUtil.erasure(type);
      if (erased != null) {
        type = erased;
      }
    }

    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(@Nonnull StringBuilder builder, @Nullable PsiType type, @Nonnull PsiElement context) {
    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(@Nonnull final StringBuilder builder,
                               @Nullable PsiType type,
                               @Nonnull final PsiElement context,
                               @Nonnull final ClassNameProvider classNameProvider) {
    if (type instanceof PsiPrimitiveType) {
      builder.append(type.getCanonicalText());
      return;
    }

    if (type == null) {
      builder.append(CommonClassNames.JAVA_LANG_OBJECT);
      return;
    }

    if (type instanceof GrAnonymousClassType) {
      type = ((GrAnonymousClassType)type).getSimpleClassType();
    }

    final boolean acceptEllipsis = isLastParameter(context);

    type.accept(new TypeWriter(builder, classNameProvider, acceptEllipsis, context));
  }

  private TypeWriter(@Nonnull StringBuilder builder,
                     @Nonnull ClassNameProvider classNameProvider,
                     boolean acceptEllipsis,
                     @Nonnull PsiElement context) {
    this.acceptEllipsis = acceptEllipsis;
    this.builder = builder;
    this.classNameProvider = classNameProvider;
    this.context = context;
  }

  @Override
  public Object visitEllipsisType(@Nonnull PsiEllipsisType ellipsisType) {
    final PsiType componentType = ellipsisType.getComponentType();
    componentType.accept(this);
    if (acceptEllipsis) {
      builder.append("...");
    }
    else {
      builder.append("[]");
    }
    return this;
  }

  @Override
  public Object visitPrimitiveType(@Nonnull PsiPrimitiveType primitiveType) {
    if (classNameProvider.forStubs()) {
      builder.append(primitiveType.getCanonicalText());
      return this;
    }
    final PsiType boxed = TypesUtil.boxPrimitiveType(primitiveType, context.getManager(), context.getResolveScope());
    boxed.accept(this);
    return this;
  }

  @Override
  public Object visitArrayType(@Nonnull PsiArrayType arrayType) {
    arrayType.getComponentType().accept(this);
    builder.append("[]");
    return this;
  }

  @Override
  public Object visitClassType(@Nonnull PsiClassType classType) {
    final PsiType[] parameters = classType.getParameters();
    final PsiClass psiClass = classType.resolve();
    if (psiClass == null) {
      builder.append(classType.getClassName());
    }
    else {
      final String qname = classNameProvider.getQualifiedClassName(psiClass, context);
      builder.append(qname);
    }
    GenerationUtil.writeTypeParameters(builder, parameters, context, classNameProvider);
    return this;
  }

  @Override
  public Object visitCapturedWildcardType(@Nonnull PsiCapturedWildcardType capturedWildcardType) {
    capturedWildcardType.getWildcard().accept(this);
    return this;
  }

  @Override
  public Object visitWildcardType(@Nonnull PsiWildcardType wildcardType) {
    builder.append('?');
    PsiType bound = wildcardType.getBound();
    if (bound == null) return this;
    if (wildcardType.isExtends()) {
      builder.append(" extends ");
    }
    else {
      builder.append(" super ");
    }
    bound.accept(this);
    return this;
  }

  @Override
  public Object visitDisjunctionType(@Nonnull PsiDisjunctionType disjunctionType) {
    //it is not available in groovy source code
    throw new UnsupportedOperationException();
  }

  @Override
  public Object visitType(@Nonnull PsiType type) {
    throw new UnsupportedOperationException();
  }

  private static boolean isLastParameter(@Nonnull PsiElement context) {
    if (context instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)context).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiParameter[] parameters = ((PsiMethod)scope).getParameterList().getParameters();
        return parameters.length > 0 && parameters[parameters.length - 1] == context;
      }
    }
    return false;
  }

}
