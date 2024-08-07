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

package org.jetbrains.plugins.groovy.impl.intentions.style;

import com.intellij.java.language.impl.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.java.language.psi.infos.CandidateInfo;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.impl.refactoring.DefaultGroovyVariableNameValidator;
import org.jetbrains.plugins.groovy.impl.refactoring.GroovyNameSuggestionUtil;

import jakarta.annotation.Nonnull;

import java.util.*;

/**
 * @author Maxim.Medvedev
 */
public class ReplaceAbstractClassInstanceByMapIntention extends Intention {
  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }

  @Override
  protected void processIntention(@Nonnull PsiElement psiElement, Project project, Editor editor) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    GrCodeReferenceElement ref = (GrCodeReferenceElement)psiElement;
    final GrAnonymousClassDefinition anonymous = (GrAnonymousClassDefinition)ref.getParent();
    final GrNewExpression newExpr = (GrNewExpression)anonymous.getParent();

    final PsiElement resolved = ref.resolve();
    assert resolved instanceof PsiClass;// && ((PsiClass)resolved).isInterface();

    GrTypeDefinitionBody body = anonymous.getBody();
    assert body != null;

    List<Pair<PsiMethod, GrOpenBlock>> methods = new ArrayList<Pair<PsiMethod, GrOpenBlock>>();
    for (GrMethod method : body.getMethods()) {
      methods.add(new Pair<PsiMethod, GrOpenBlock>(method, method.getBlock()));
    }

    final PsiClass iface = (PsiClass)resolved;
    final Collection<CandidateInfo> collection = OverrideImplementExploreUtil.getMethodsToOverrideImplement(anonymous, true);
    for (CandidateInfo info : collection) {
      methods.add(new Pair<PsiMethod, GrOpenBlock>((PsiMethod)info.getElement(), null));
    }

    StringBuilder buffer = new StringBuilder();
    if (methods.size() == 1) {
      final Pair<PsiMethod, GrOpenBlock> pair = methods.get(0);
      appendClosureTextByMethod(pair.getFirst(), buffer, pair.getSecond(), newExpr);
    }
    else {
      buffer.append("[");
      buffer.append("\n");
      for (Pair<PsiMethod, GrOpenBlock> pair : methods) {
        final PsiMethod method = pair.getFirst();
        final GrOpenBlock block = pair.getSecond();
        buffer.append(method.getName()).append(": ");
        appendClosureTextByMethod(method, buffer, block, newExpr);
        buffer.append(",\n");
      }
      if (methods.size() > 0) {
        buffer.delete(buffer.length() - 2, buffer.length());
        buffer.append('\n');
      }
      buffer.append("]");
    }
    buffer.append(" as ").append(iface.getQualifiedName());
    createAndAdjustNewExpression(project, newExpr, buffer);
  }

  private static void createAndAdjustNewExpression(final Project project,
                                                   final GrNewExpression newExpression,
                                                   final StringBuilder buffer) throws IncorrectOperationException {
    final GrExpression expr = GroovyPsiElementFactory.getInstance(project).createExpressionFromText(buffer.toString());
    final GrExpression safeTypeExpr = newExpression.replaceWithExpression(expr, false);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(safeTypeExpr);
  }

  private static void appendClosureTextByMethod(final PsiMethod method,
                                                final StringBuilder buffer,
                                                @Nullable GrOpenBlock block,
                                                GroovyPsiElement context) {
    final PsiParameterList list = method.getParameterList();
    buffer.append("{ ");
    final PsiParameter[] parameters = list.getParameters();
    Set<String> generatedNames = new HashSet<String>();
    if (parameters.length > 0) {
      final PsiParameter first = parameters[0];
      final PsiType type = first.getType();
      buffer.append(type.getCanonicalText()).append(" ");
      buffer.append(createName(generatedNames, first, type, context));
    }
    for (int i = 1; i < parameters.length; i++) {
      buffer.append(", ");
      final PsiParameter param = parameters[i];
      final PsiType type = param.getType();
      buffer.append(type.getCanonicalText()).append(" ");
      String name = createName(generatedNames, param, type, context);
      buffer.append(name);
    }
    if (parameters.length > 0) {
      buffer.append(" ->");
    }

    if (block != null) {
      final PsiElement lBrace = block.getLBrace();
      final PsiElement rBrace = block.getRBrace();
      for (PsiElement child = lBrace != null ? lBrace.getNextSibling() : block.getFirstChild();
           child != null && child != rBrace;
           child = child.getNextSibling()) {
        buffer.append(child.getText());
      }
    }
    buffer.append(" }");
  }

  private static String createName(final Set<String> generatedNames, final PsiParameter param, final PsiType type, GroovyPsiElement context) {
    String name = param.getName();
    if (name == null) {
      name = GroovyNameSuggestionUtil.suggestVariableNameByType(type, new DefaultGroovyVariableNameValidator(context, generatedNames))[0];
      assert name != null;
    }
    generatedNames.add(name);
    return name;
  }

  static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (element instanceof GrCodeReferenceElement && element.getParent() instanceof GrAnonymousClassDefinition) {
        final GrAnonymousClassDefinition anonymous = ((GrAnonymousClassDefinition)element.getParent());
        GrNewExpression newExpression = (GrNewExpression)anonymous.getParent();
        if (newExpression.getQualifier() == null && anonymous.getFields().length == 0) {
          return true;
        }
      }
      return false;
    }
  }
}


