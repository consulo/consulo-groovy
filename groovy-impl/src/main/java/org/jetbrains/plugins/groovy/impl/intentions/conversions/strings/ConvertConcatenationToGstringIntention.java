/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.intentions.conversions.strings;

import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.MethodSignatureUtil;
import com.intellij.java.language.psi.util.TypeConversionUtil;
import consulo.application.AccessToken;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.refactoring.introduce.IntroduceTargetChooser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.impl.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ConvertConcatenationToGstringIntention extends Intention {
  private static final String END_BRACE = "}";
  private static final String START_BRACE = "${";

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new MyPredicate();
  }


  private static List<GrExpression> collectExpressions(final PsiFile file, final int offset) {
    final List<GrExpression> expressions = new ArrayList<GrExpression>();

    _collect(file, offset, expressions);
    if (expressions.isEmpty()) _collect(file, offset, expressions);
    return expressions;
  }

  private static void _collect(PsiFile file, int offset, List<GrExpression> expressions) {
    final PsiElement elementAtCaret = file.findElementAt(offset);
    for (GrExpression expression = PsiTreeUtil.getParentOfType(elementAtCaret, GrExpression.class);
         expression != null;
         expression = PsiTreeUtil.getParentOfType(expression, GrExpression.class)) {
      if (MyPredicate.satisfied(expression)) expressions.add(expression);
      else if (!expressions.isEmpty()) break;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final PsiFile file = element.getContainingFile();
    final int offset = editor.getCaretModel().getOffset();
    final List<GrExpression> expressions = ReadAction.compute(() -> collectExpressions(file, offset));

    final Document document = editor.getDocument();
    if (expressions.size() == 1) {
      invokeImpl(expressions.get(0), document);
    }
    else if (expressions.size() > 0) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        invokeImpl(expressions.get(expressions.size() - 1), document);
        return;
      }
      IntroduceTargetChooser.showChooser(editor, expressions,
                                         selectedValue -> invokeImpl(selectedValue, document),
                                         grExpression -> grExpression.getText()
      );
    }
  }

  private static void invokeImpl(final PsiElement element, Document document) {
    boolean isMultiline = containsMultilineStrings((GrExpression)element);

    StringBuilder builder = new StringBuilder(element.getTextLength());
    if (element instanceof GrBinaryExpression) {
      performIntention((GrBinaryExpression)element, builder, isMultiline);
    }
    else if (element instanceof GrLiteral) {
      getOperandText((GrExpression)element, builder, isMultiline);
    }
    else {
      return;
    }

    String text = builder.toString();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(element.getProject());
    final GrExpression newExpr = factory.createExpressionFromText(GrStringUtil.addQuotes(text, true));

    CommandProcessor.getInstance().executeCommand(element.getProject(), new Runnable() {
      @Override
      public void run() {
        final AccessToken accessToken = WriteAction.start();
        try {
          final GrExpression expression = ((GrExpression)element).replaceWithExpression(newExpr, true);
          if (expression instanceof GrString) {
            GrStringUtil.removeUnnecessaryBracesInGString((GrString)expression);
          }
        }
        finally {
          accessToken.finish();
        }
      }
    }, null, null, document);
  }

  private static boolean containsMultilineStrings(GrExpression expr) {
    final Ref<Boolean> result = Ref.create(false);
    expr.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitLiteralExpression(GrLiteral literal) {
        final String quote = GrStringUtil.getStartQuote(literal.getText());
        if ("'''".equals(quote) || "\"\"\"".equals(quote)) {
          result.set(true);
        }
      }

      @Override
      public void visitElement(GroovyPsiElement element) {
        if (!result.get()) {
          super.visitElement(element);
        }
      }
    });
    return result.get();
  }

  private static void performIntention(GrBinaryExpression expr, StringBuilder builder, boolean multiline) {
    GrExpression left = (GrExpression)skipParentheses(expr.getLeftOperand(), false);
    GrExpression right = (GrExpression)skipParentheses(expr.getRightOperand(), false);
    getOperandText(left, builder, multiline);
    getOperandText(right, builder, multiline);
  }

  private static void getOperandText(@Nullable GrExpression operand, StringBuilder builder, boolean multiline) {
    if (operand instanceof GrRegex) {
      StringBuilder b = new StringBuilder();
      GrStringUtil.parseRegexCharacters(GrStringUtil.removeQuotes(operand.getText()), b, null, operand.getText().startsWith("/"));
      GrStringUtil.escapeSymbolsForGString(b, !multiline, false);
    }
    else if (operand instanceof GrString) {
      final String text = GrStringUtil.removeQuotes(operand.getText());
      if (multiline && ((GrString)operand).isPlainString()) {
        final StringBuilder buffer = new StringBuilder(text);
        GrStringUtil.unescapeCharacters(buffer, "\"", true);
        builder.append(buffer);
      }
      else {
        builder.append(text);
      }
    }
    else if (operand instanceof GrLiteral) {
      String text = GrStringUtil.removeQuotes(operand.getText());
      if (multiline) {
        final int position = builder.length();
        GrStringUtil.escapeAndUnescapeSymbols(text, "$", "'\"", builder);
        GrStringUtil.fixAllTripleDoubleQuotes(builder, position);
      }
      else {
        GrStringUtil.escapeAndUnescapeSymbols(text, "$\"", "'", builder);
      }
    }
    else if (MyPredicate.satisfied(operand)) {
      performIntention((GrBinaryExpression)operand, builder, multiline);
    }
    else if (isToStringMethod(operand, builder)) {
      //nothing to do
    }
    else {
      builder.append(START_BRACE).append(operand == null ? "" : operand.getText()).append(END_BRACE);
    }
  }

  /**
   * append text to builder if the operand is 'something'.toString()
   */
  private static boolean isToStringMethod(GrExpression operand, StringBuilder builder) {
    if (!(operand instanceof GrMethodCallExpression)) return false;

    final GrExpression expression = ((GrMethodCallExpression)operand).getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return false;

    final GrReferenceExpression refExpr = (GrReferenceExpression)expression;
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) return false;

    final GroovyResolveResult[] results = refExpr.multiResolve(false);
    if (results.length != 1) return false;

    final PsiElement element = results[0].getElement();
    if (!(element instanceof PsiMethod)) return false;

    final PsiMethod method = (PsiMethod)element;
    final PsiClass objectClass =
      JavaPsiFacade.getInstance(operand.getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, operand.getResolveScope());
    if (objectClass == null) return false;

    final PsiMethod[] toStringMethod = objectClass.findMethodsByName("toString", true);
    if (MethodSignatureUtil.isSubsignature(toStringMethod[0].getHierarchicalMethodSignature(), method.getHierarchicalMethodSignature())) {
      builder.append(START_BRACE).append(qualifier.getText()).append(END_BRACE);
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiElement skipParentheses(PsiElement element, boolean up) {
    if (up) {
      PsiElement parent = element.getParent();
      while (parent instanceof GrParenthesizedExpression) {
        parent = parent.getParent();
      }
      return parent;
    }
    else {
      while (element instanceof GrParenthesizedExpression) {
        element = ((GrParenthesizedExpression)element).getOperand();
      }
      return element;
    }
  }

  private static class MyPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      return satisfied(element);
    }

    public static boolean satisfied(PsiElement element) {
      if (element instanceof GrLiteral &&
        ((GrLiteral)element).getValue() instanceof String &&
        GrLiteralImpl.getLiteralType((GrLiteral)element) != GroovyTokenTypes.mGSTRING_LITERAL) {
        return true;
      }

      if (!(element instanceof GrBinaryExpression)) return false;

      GrBinaryExpression binaryExpression = (GrBinaryExpression)element;
      if (!GroovyTokenTypes.mPLUS.equals(binaryExpression.getOperationTokenType())) return false;

      if (ErrorUtil.containsError(element)) return false;

      final PsiType type = binaryExpression.getType();
      if (type == null) return false;

      final PsiClassType stringType = TypesUtil.createType(CommonClassNames.JAVA_LANG_STRING, element);
      final PsiClassType gstringType = TypesUtil.createType(GroovyCommonClassNames.GROOVY_LANG_GSTRING, element);
      if (!(TypeConversionUtil.isAssignable(stringType, type) || TypeConversionUtil.isAssignable(gstringType, type))) return false;

      return true;
    }
  }
}
