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
package org.jetbrains.plugins.groovy.impl.codeInspection.bugs;

import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.impl.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.impl.dsl.psi.PsiClassCategory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import javax.annotation.Nonnull;

/**
 * @author Max Medvedev
 */
public class NewInstanceOfSingletonInspection extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(NewInstanceOfSingletonInspection.class);

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitNewExpression(GrNewExpression newExpression) {
        super.visitNewExpression(newExpression);

        final GrCodeReferenceElement refElement = newExpression.getReferenceElement();
        if (refElement == null) return;
        if (newExpression.getArrayDeclaration() != null) return;

        final PsiElement resolved = refElement.resolve();
        if (resolved instanceof GrTypeDefinition &&
            PsiClassCategory.hasAnnotation((GrTypeDefinition)resolved, GroovyCommonClassNames.GROOVY_LANG_SINGLETON)) {
          registerError(newExpression, GroovyInspectionBundle.message("new.instance.of.singleton"));
        }
      }
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected GroovyFix buildFix(final PsiElement location) {
    final GrCodeReferenceElement refElement = ((GrNewExpression)location).getReferenceElement();
    LOG.assertTrue(refElement != null);
    final GrTypeDefinition singleton = (GrTypeDefinition)refElement.resolve();
    LOG.assertTrue(singleton != null);

    return new GroovyFix() {
      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException
	  {
        final GrExpression instanceRef =
          GroovyPsiElementFactory.getInstance(project).createExpressionFromText(singleton.getQualifiedName() + ".instance");

        final GrExpression replaced = ((GrNewExpression)location).replaceWithExpression(instanceRef, true);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
      }

      @Nonnull
      @Override
      public String getName() {
        return GroovyInspectionBundle.message("replace.new.expression.with.0.instance", singleton.getName());
      }
    };
  }

  @Nls
  @Nonnull
  @Override
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Override
  protected String buildErrorString(Object... args) {
    return (String)args[0];
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "New instance of class annotated with @groovy.lang.Singleton";
  }
}
