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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import com.intellij.java.language.impl.psi.impl.light.LightMethodBuilder;
import com.intellij.java.language.impl.psi.scope.NameHint;
import com.intellij.java.language.psi.*;
import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.collection.ArrayUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstantList;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import jakarta.annotation.Nullable;

/**
 * @author Dmitry.Krasilschikov
 * @date 18.03.2007
 */
public class GrEnumTypeDefinitionImpl extends GrTypeDefinitionImpl implements GrEnumTypeDefinition {
  @NonNls
  private static final String JAVA_LANG_ENUM = "java.lang.Enum";
  private static final String ENUM_SIMPLE_NAME = "Enum";

  public GrEnumTypeDefinitionImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrEnumTypeDefinitionImpl(GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.ENUM_DEFINITION);
  }

  public String toString() {
    return "Enumeration definition";
  }

  @Override
  public GrEnumDefinitionBody getBody() {
    return getStubOrPsiChild(GroovyElementTypes.ENUM_BODY);
  }

  @Override
  public boolean isEnum() {
    return true;
  }

  @Override
  @Nonnull
  public PsiClassType[] getExtendsListTypes() {
    return new PsiClassType[]{createEnumType(), createGroovyObjectSupportType()};
  }

  @Override
  protected String[] getExtendsNames() {
    return new String[]{ENUM_SIMPLE_NAME};
  }

  private PsiClassType createEnumType() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
    PsiClass enumClass = facade.findClass(JAVA_LANG_ENUM, getResolveScope());
    PsiElementFactory factory = facade.getElementFactory();
    if (enumClass != null) {
      PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
      PsiTypeParameter[] typeParameters = enumClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor = substitutor.put(typeParameters[0], factory.createType(this));
      }

      return factory.createType(enumClass, substitutor);
    }
    return TypesUtil.createTypeByFQClassName(JAVA_LANG_ENUM, this);
  }

  private PsiClassType createGroovyObjectSupportType() {
    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT, this);
  }

  @Override
  @Nonnull
  public GrField[] getFields() {
    GrField[] bodyFields = super.getFields();
    GrEnumConstant[] enumConstants = getEnumConstants();
    if (bodyFields.length == 0) return enumConstants;
    if (enumConstants.length == 0) return bodyFields;
    return ArrayUtil.mergeArrays(bodyFields, enumConstants);
  }

  @Override
  public boolean processDeclarations(@Nonnull PsiScopeProcessor processor,
                                     @Nonnull ResolveState state,
                                     @Nullable PsiElement lastParent,
                                     @Nonnull PsiElement place) {
    if (ResolveUtil.shouldProcessMethods(processor.getHint(ClassHint.KEY))) {
      final NameHint nameHint = processor.getHint(NameHint.KEY);
      final String name = nameHint == null ? null : nameHint.getName(state);
      for (PsiMethod method : getDefEnumMethods()) {
        if (name == null || name.equals(method.getName())) {
          if (!processor.execute(method, state)) return false;
        }
      }
    }

    return super.processDeclarations(processor, state, lastParent, place);
  }

  private PsiMethod[] getDefEnumMethods() {
    return LanguageCachedValueUtil.getCachedValue(this, new CachedValueProvider<PsiMethod[]>() {
      @Override
      public Result<PsiMethod[]> compute() {
        PsiMethod[] defMethods = new PsiMethod[4];
        final PsiManager manager = getManager();
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        defMethods[0] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "values")
          .setMethodReturnType(factory.createTypeFromText(CommonClassNames.JAVA_UTIL_COLLECTION + "<" + getName() + ">", GrEnumTypeDefinitionImpl.this))
          .setContainingClass(GrEnumTypeDefinitionImpl.this)
          .addModifier(PsiModifier.PUBLIC)
          .addModifier(PsiModifier.STATIC);

        defMethods[1] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "next")
          .setMethodReturnType(factory.createType(GrEnumTypeDefinitionImpl.this))
          .setContainingClass(GrEnumTypeDefinitionImpl.this)
          .addModifier(PsiModifier.PUBLIC);

        defMethods[2] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "previous")
          .setMethodReturnType(factory.createType(GrEnumTypeDefinitionImpl.this))
          .setContainingClass(GrEnumTypeDefinitionImpl.this)
          .addModifier(PsiModifier.PUBLIC);

        defMethods[3] = new LightMethodBuilder(manager, GroovyLanguage.INSTANCE, "valueOf")
          .setMethodReturnType(factory.createType(GrEnumTypeDefinitionImpl.this))
          .setContainingClass(GrEnumTypeDefinitionImpl.this)
          .addParameter("name", CommonClassNames.JAVA_LANG_STRING)
          .addModifier(PsiModifier.PUBLIC)
          .addModifier(PsiModifier.STATIC);

        return Result.create(defMethods, GrEnumTypeDefinitionImpl.this);
      }
    });
  }

  @Override
  public GrEnumConstant[] getEnumConstants() {
    GrEnumConstantList list = getEnumConstantList();
    if (list != null) return list.getEnumConstants();
    return GrEnumConstant.EMPTY_ARRAY;
  }

  @Override
  public GrEnumConstantList getEnumConstantList() {
    GrEnumDefinitionBody enumDefinitionBody = getBody();
    if (enumDefinitionBody != null) return enumDefinitionBody.getEnumConstantList();
    return null;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitEnumDefinition(this);
  }
}
