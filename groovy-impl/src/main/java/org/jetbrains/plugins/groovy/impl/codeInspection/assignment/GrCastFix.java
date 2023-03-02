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

import com.intellij.java.language.psi.PsiType;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.impl.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrThrowStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import javax.annotation.Nonnull;

/**
 * @author Maxim.Medvedev
 */
public class GrCastFix extends GroovyFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(GrCastFix.class);
  private final PsiType myExpectedType;

  public GrCastFix(PsiType expectedType, GrExpression expression) {
    myExpectedType = PsiImplUtil.normalizeWildcardTypeByPosition(expectedType, expression);
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
    final GrExpression cast = findExpressionToCast(descriptor);
    if (cast == null) return;
    doCast(project, myExpectedType, cast);
  }

  private static GrExpression findExpressionToCast(ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getInitializerGroovy();
    }
    else if (parent instanceof GrAssignmentExpression) {
      return ((GrAssignmentExpression)parent).getRValue();
    }
    else if (parent instanceof GrThrowStatement) {
      return ((GrThrowStatement)parent).getException();
    }
    else if (parent instanceof GrReturnStatement) {
      return ((GrReturnStatement)parent).getReturnValue();
    }
    else if (element instanceof GrExpression) {
      return (GrExpression)element;
    }
    PsiFile file = element.getContainingFile();
    VirtualFile virtualFile = file.getVirtualFile();
    String url = virtualFile == null ? "" : virtualFile.getPresentableUrl();
    LOG.error("can't find expression to cast at position " + element.getTextRange(), AttachmentFactory.get().create(url, file.getText()));
    return null;
  }

  static void doCast(@Nonnull Project project, @Nonnull PsiType type, @Nonnull GrExpression expr) {
    if (!type.isValid()) return;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    final GrSafeCastExpression cast = (GrSafeCastExpression)factory.createExpressionFromText("foo as String");
    final GrTypeElement typeElement = factory.createTypeElement(type);
    cast.getOperand().replaceWithExpression(expr, true);
    cast.getCastTypeElement().replace(typeElement);

    final GrExpression replaced = expr.replaceWithExpression(cast, true);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
  }

  @Nonnull
  @Override
  public String getName() {
    return "Cast to " + myExpectedType.getPresentableText();
  }
}
