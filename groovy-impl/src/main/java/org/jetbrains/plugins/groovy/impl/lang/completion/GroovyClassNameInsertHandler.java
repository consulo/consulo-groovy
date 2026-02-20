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
package org.jetbrains.plugins.groovy.impl.lang.completion;

import com.intellij.java.impl.codeInsight.completion.AllClassesGetter;
import com.intellij.java.impl.codeInsight.completion.JavaCompletionUtil;
import com.intellij.java.impl.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiArrayType;
import com.intellij.java.language.psi.PsiClass;
import consulo.codeEditor.Editor;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

import jakarta.annotation.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class GroovyClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  @Nullable
  private static GrNewExpression findNewExpression(@Nullable PsiElement position) {
    if (position == null) return null;
    PsiElement reference = position.getParent();
    if (!(reference instanceof GrCodeReferenceElement)) return null;

    PsiElement parent = reference.getParent();
    while (parent instanceof GrCodeReferenceElement) parent = parent.getParent();
    if (parent instanceof GrAnonymousClassDefinition) parent = parent.getParent();
    return parent instanceof GrNewExpression ? (GrNewExpression)parent : null;
  }

  @Override
  public void handleInsert(InsertionContext context, JavaPsiClassReferenceElement item) {
    PsiFile file = context.getFile();
    Editor editor = context.getEditor();
    int endOffset = editor.getCaretModel().getOffset();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, endOffset - 1, GrImportStatement.class, false) != null ||
      !(file instanceof GroovyFileBase)) {
      AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      return;
    }

    PsiDocumentManager.getInstance(context.getProject()).commitDocument(editor.getDocument());

    PsiElement position = file.findElementAt(endOffset - 1);

    boolean parens = shouldInsertParentheses(position);

    PsiClass psiClass = item.getObject();
    if (isInVariable(position) || GroovyCompletionUtil.isInPossibleClosureParameter(position)) {
      Project project = context.getProject();
      String qname = psiClass.getQualifiedName();
      String shortName = psiClass.getName();
      if (qname == null) return;

      PsiClass aClass = JavaPsiFacade.getInstance(project).getResolveHelper().resolveReferencedClass(shortName, position);
      if (aClass == null) {
        ((GroovyFileBase)file).addImportForClass(psiClass);
        return;
      }
      else if (aClass == CompletionUtilCore.getOriginalOrSelf(psiClass)) {
        return;
      }
    }
    AllClassesGetter.TRY_SHORTENING.handleInsert(context, item);

    if (parens && context.getCompletionChar() != '[') {
      int identifierEnd = context.getTailOffset();

      GroovyPsiElement place =
        PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), GroovyPsiElement.class, false);
      JavaCompletionUtil.insertParentheses(context,
                                           item,
                                           false,
                                           place != null && GroovyCompletionUtil.hasConstructorParameters(psiClass, place));

      if (context.getCompletionChar() == '<' || psiClass.hasTypeParameters() && context.getCompletionChar() != '(') {
        context.setAddCompletionChar(false);
        JavaCompletionUtil.promptTypeArgs(context, identifierEnd);
      }
    }

  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    GrNewExpression newExpression = findNewExpression(position);
    return newExpression != null && ContainerUtil.findInstance(GroovyExpectedTypesProvider.getDefaultExpectedTypes(newExpression),
                                                               PsiArrayType.class) == null;
  }

  private static boolean isInVariable(@Nullable PsiElement position) {
    if (position == null) {
      return false;
    }

    PsiElement parent = position.getParent();
    if (parent instanceof GrVariable) {
      return ((GrVariable)parent).getTypeElementGroovy() == null && position == ((GrVariable)parent).getNameIdentifierGroovy();
    }
    if (parent instanceof GrCatchClause) {
      return ((GrCatchClause)parent).getParameter() == null;
    }

    return false;
  }
}
