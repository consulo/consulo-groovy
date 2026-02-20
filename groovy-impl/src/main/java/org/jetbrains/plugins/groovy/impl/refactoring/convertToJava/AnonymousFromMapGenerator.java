/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.ModifierListGenerator.JAVA_MODIFIERS_WITHOUT_ABSTRACT;
import static org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.TypeWriter.writeType;
import static org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.TypeWriter.writeTypeForNew;

/**
 * @author Maxim.Medvedev
 */
public class AnonymousFromMapGenerator {
  private AnonymousFromMapGenerator() {
  }

  static void writeAnonymousMap(GrListOrMap operand, GrTypeElement typeElement, StringBuilder builder, ExpressionContext context) {
    PsiType type = typeElement.getType();
    PsiClass psiClass;
    PsiSubstitutor substitutor;
    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)type).resolveGenerics();
      psiClass = resolveResult.getElement();
      substitutor = resolveResult.getSubstitutor();
    }
    else {
      psiClass = null;
      substitutor = PsiSubstitutor.EMPTY;
    }
    builder.append("new ");
    writeTypeForNew(builder, type, operand);
    builder.append("() {\n");

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(operand.getProject());

    GrExpression caller = factory.createExpressionFromText("this");
    for (GrNamedArgument arg : operand.getNamedArguments()) {
      String name = arg.getLabelName();
      GrExpression expression = arg.getExpression();
      if (name == null || expression == null || !(expression instanceof GrClosableBlock)) continue;

      GrParameter[] allParameters = ((GrClosableBlock)expression).getAllParameters();
      List<GrParameter> actual = new ArrayList<GrParameter>(Arrays.asList(allParameters));
      PsiType clReturnType = ((GrClosableBlock)expression).getReturnType();

      GrExpression[] args = new GrExpression[allParameters.length];
      for (int i = 0; i < allParameters.length; i++) {
        args[i] = factory.createExpressionFromText(allParameters[i].getName());
      }

      for (int param = allParameters.length; param >= 0; param--) {


        if (param < allParameters.length && !actual.get(param).isOptional()) continue;

        if (param < allParameters.length) {
          GrParameter opt = actual.remove(param);
          args[param] = opt.getInitializerGroovy();
        }

        GrParameter[] parameters = actual.toArray(new GrParameter[actual.size()]);

        GrClosureSignature signature = GrClosureSignatureUtil.createSignature(parameters, clReturnType);
        GrMethod pattern = factory.createMethodFromSignature(name, signature);

        PsiMethod found = null;
        if (psiClass != null) {
          found = psiClass.findMethodBySignature(pattern, true);
        }

        if (found != null) {
          ModifierListGenerator.writeModifiers(builder, found.getModifierList(), JAVA_MODIFIERS_WITHOUT_ABSTRACT);
        }
        else {
          builder.append("public ");
        }

        PsiType returnType;
        if (found != null) {
          returnType = substitutor.substitute(context.typeProvider.getReturnType(found));
        }
        else {
          returnType = signature.getReturnType();
        }

        writeType(builder, returnType, operand);

        builder.append(' ').append(name);
        GenerationUtil.writeParameterList(builder, parameters, new GeneratorClassNameProvider(), context);

        ExpressionContext extended = context.extend();
        extended.setInAnonymousContext(true);
        if (param == allParameters.length) {
          new CodeBlockGenerator(builder, extended).generateCodeBlock((GrCodeBlock)expression, false);
        }
        else {
          builder.append("{\n");
          ExpressionGenerator expressionGenerator = new ExpressionGenerator(builder, extended);
          GenerationUtil.invokeMethodByName(caller, name, args, GrNamedArgument.EMPTY_ARRAY, GrClosableBlock.EMPTY_ARRAY,
                                            expressionGenerator, arg);

          builder.append(";\n}\n");
        }
      }
    }

    builder.append("}");
  }
}
