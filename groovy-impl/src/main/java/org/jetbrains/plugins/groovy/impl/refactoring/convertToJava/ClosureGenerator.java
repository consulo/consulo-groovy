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
package org.jetbrains.plugins.groovy.impl.refactoring.convertToJava;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.PsiPrimitiveType;
import com.intellij.java.language.psi.PsiType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.logging.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.impl.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import jakarta.annotation.Nonnull;
import java.util.Collection;

import static org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.TypeWriter.writeType;
import static org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.TypeWriter.writeTypeForNew;

/**
 * @author Maxim.Medvedev
 */
public class ClosureGenerator {
  private static final Logger LOG = Logger.getInstance(ClosureGenerator.class);

  public static final String[] MODIFIERS = new String[]{PsiModifier.PUBLIC};

  private final StringBuilder builder;
  private final ExpressionContext context;

  public ClosureGenerator(@Nonnull StringBuilder builder, @Nonnull ExpressionContext context) {
    this.builder = builder;
    this.context = context;
  }

  public void generate(@Nonnull GrClosableBlock closure) {
    builder.append("new ");
    writeTypeForNew(builder, closure.getType(), closure);
    builder.append('(');

    final CharSequence owner = getOwner(closure);
    builder.append(owner);
    builder.append(", ");
    builder.append(owner);

    builder.append(") {\n");

    generateClosureMainMethod(closure);

    final ClassItemGeneratorImpl generator = new ClassItemGeneratorImpl(context);
    final GrMethod method = generateClosureMethod(closure);
    final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();

    if (reflectedMethods.length > 0) {
      for (GrReflectedMethod reflectedMethod : reflectedMethods) {
        if (reflectedMethod.getSkippedParameters().length > 0) {
          generator.writeMethod(builder, reflectedMethod);
          builder.append('\n');
        }
      }
    }
    builder.append('}');
  }

  private void generateClosureMainMethod(@Nonnull GrClosableBlock block) {
    builder.append("public ");
    final PsiType returnType = block.getReturnType();
    writeType(builder, returnType, block);
    builder.append(" doCall");
    final GrParameter[] parameters = block.getAllParameters();
    GenerationUtil.writeParameterList(builder, parameters, new GeneratorClassNameProvider(), context);

    Collection<GrStatement> myExitPoints = ControlFlowUtils.collectReturns(block);
    boolean shouldInsertReturnNull =
      !(returnType instanceof PsiPrimitiveType) && MissingReturnInspection.methodMissesSomeReturns(block, MissingReturnInspection.ReturnStatus.shouldNotReturnValue);

    new CodeBlockGenerator(builder, context.extend(), myExitPoints).generateCodeBlock(block, shouldInsertReturnNull);
    builder.append('\n');
  }

  @Nonnull
  private GrMethod generateClosureMethod(@Nonnull GrClosableBlock block) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.project);
    final GrMethod method = factory.createMethodFromText("def doCall(){}", block);

    method.setReturnType(block.getReturnType());
    if (block.hasParametersSection()) {
      method.getParameterList().replace(block.getParameterList());
    }
    else {
      final GrParameter[] allParameters = block.getAllParameters();
      LOG.assertTrue(allParameters.length == 1);
      final GrParameter itParameter = allParameters[0];
      final GrParameter parameter = factory.createParameter("it", itParameter.getType().getCanonicalText(), "null", block);
      method.getParameterList().add(parameter);
    }
    ((GroovyFileImpl)method.getContainingFile()).setContextNullable(null);
    return method;
  }

  @NonNls
  @Nonnull
  private CharSequence getOwner(@Nonnull GrClosableBlock closure) {
    final GroovyPsiElement context = PsiTreeUtil.getParentOfType(closure, GrMember.class, GroovyFile.class);
    LOG.assertTrue(context != null);

    final PsiClass contextClass;
    if (context instanceof GroovyFile) {
      contextClass = ((GroovyFile)context).getScriptClass();
    }
    else if (context instanceof PsiClass) {
      contextClass = (PsiClass)context;
    }
    else if (context instanceof GrMember) {
      if (((GrMember)context).hasModifierProperty(PsiModifier.STATIC)) {
        contextClass = null; //no context class
      }
      else {
        contextClass = ((GrMember)context).getContainingClass();
      }
    }
    else {
      contextClass = null;
    }

    if (contextClass == null) return "null";

    final PsiElement implicitClass = GenerationUtil.getWrappingImplicitClass(closure);
    if (implicitClass == null) {
      return "this";
    }
    else {
      final StringBuilder buffer = new StringBuilder();
      GenerationUtil.writeThisReference(contextClass, buffer, this.context);
      return buffer;
    }
  }
}
