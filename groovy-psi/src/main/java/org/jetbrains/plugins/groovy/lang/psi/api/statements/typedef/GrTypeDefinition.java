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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.psi.NavigatablePsiElement;
import consulo.util.collection.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocCommentOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public interface GrTypeDefinition
  extends GrTopStatement, NavigatablePsiElement, PsiClass, GrTopLevelDefinition, GrDocCommentOwner, GrMember {

  public static final GrTypeDefinition[] EMPTY_ARRAY = new GrTypeDefinition[0];

  public static ArrayFactory<GrTypeDefinition> ARRAY_FACTORY = new ArrayFactory<GrTypeDefinition>() {
    @Nonnull
    @Override
    public GrTypeDefinition[] create(int count) {
      return count == 0 ? EMPTY_ARRAY : new GrTypeDefinition[count];
    }
  };

  boolean isTrait();

  @Nullable
  GrTypeDefinitionBody getBody();

  @Override
  @Nonnull
  GrField[] getFields();

  @Nonnull
  GrField[] getCodeFields();

  @Nonnull
  GrMethod[] getCodeConstructors();

  @Nullable
  PsiField findCodeFieldByName(String name, boolean checkBases);

  @Override
  @Nonnull
  GrClassInitializer[] getInitializers();

  @Nonnull
  GrMembersDeclaration[] getMemberDeclarations();

  @Override
  @Nullable
  String getQualifiedName();

  @Nullable
  GrExtendsClause getExtendsClause();

  @Nullable
  GrImplementsClause getImplementsClause();

  String[] getSuperClassNames();

  @Nonnull
  GrMethod[] getCodeMethods();

  @Nonnull
  PsiMethod[] findCodeMethodsByName(@NonNls String name, boolean checkBases);

  @Nonnull
  PsiMethod[] findCodeMethodsBySignature(PsiMethod patternMethod, boolean checkBases);

  boolean isAnonymous();

  @Override
  @Nullable
  String getName();

  @Override
  GrTypeParameterList getTypeParameterList();
}
