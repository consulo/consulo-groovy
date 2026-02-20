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

import com.intellij.java.language.psi.PsiModifier;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.RecursionManager;
import consulo.application.util.function.Computable;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Condition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GrScriptField extends GrLightField {
  public static final GrScriptField[] EMPTY_ARRAY = new GrScriptField[0];

  private GrScriptField(@Nonnull GrVariable original, @Nonnull GroovyScriptClass scriptClass) {
    super(scriptClass, original.getName(), original.getType(), original);

    GrLightModifierList modifierList = getModifierList();
    for (@PsiModifier.ModifierConstant String modifier : PsiModifier.MODIFIERS) {
      if (original.hasModifierProperty(modifier)) {
        modifierList.addModifier(modifier);
      }
    }

    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      String qname = annotation.getQualifiedName();
      String annotationName = qname != null ? qname : annotation.getShortName();
      if (!GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(annotationName)) {
        modifierList.addAnnotation(annotationName);
      }
    }
  }

  @Nullable
  @Override
  public GrAccessorMethod getSetter() {
    return null;
  }

  @Nonnull
  @Override
  public GrAccessorMethod[] getGetters() {
    return GrAccessorMethod.EMPTY_ARRAY;
  }

  @Nonnull
  public static GrScriptField getScriptField(@Nonnull final GrVariable original) {
    GroovyScriptClass script = (GroovyScriptClass)((GroovyFile)original.getContainingFile()).getScriptClass();
    assert script != null;

    GrScriptField result = ContainerUtil.find(getScriptFields(script), new Condition<GrScriptField>() {
      @Override
      public boolean value(GrScriptField field) {
        return field.getNavigationElement() == original;
      }
    });
    assert result != null;

    return result;
  }

  @Nonnull
  public static GrScriptField[] getScriptFields(@Nonnull final GroovyScriptClass script) {
    return LanguageCachedValueUtil.getCachedValue(script, new CachedValueProvider<GrScriptField[]>() {
      @Override
      public Result<GrScriptField[]> compute() {
        List<GrScriptField> result = RecursionManager.doPreventingRecursion(script, true, new Computable<List<GrScriptField>>() {
          @Override
          public List<GrScriptField> compute() {
            final List<GrScriptField> result = new ArrayList<GrScriptField>();
            script.getContainingFile().accept(new GroovyRecursiveElementVisitor() {
              @Override
              public void visitVariableDeclaration(GrVariableDeclaration element) {
                if (element.getModifierList().findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD) != null) {
                  for (GrVariable variable : element.getVariables()) {
                    result.add(new GrScriptField(variable, script));
                  }
                }
                super.visitVariableDeclaration(element);
              }

              @Override
              public void visitMethod(GrMethod method) {
                //skip methods
              }

              @Override
              public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
                //skip type defs
              }


            });
            return result;
          }
        });

        if (result == null) {
          return Result.create(EMPTY_ARRAY, script.getContainingFile());
        }
        else {
          return Result.create(result.toArray(new GrScriptField[result.size()]), script.getContainingFile());
        }
      }
    });
  }

  @Nonnull
  public GrVariable getOriginalVariable() {
    return (GrVariable)getNavigationElement();
  }
}
