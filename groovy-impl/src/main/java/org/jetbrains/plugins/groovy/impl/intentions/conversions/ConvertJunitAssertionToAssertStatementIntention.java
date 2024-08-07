package org.jetbrains.plugins.groovy.impl.intentions.conversions;

import consulo.codeEditor.Editor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import org.jetbrains.plugins.groovy.impl.intentions.base.Intention;
import org.jetbrains.plugins.groovy.impl.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrAssertStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sergey Evdokimov
 */
public class ConvertJunitAssertionToAssertStatementIntention extends Intention implements PsiElementPredicate {

  private static final Pattern PATTERN = Pattern.compile("arg(\\d+)");
  
  private static Map<String, String[]> ourStatementMap = new HashMap<String, String[]>();
  static {
    ourStatementMap.put("assertNotNull", new String[]{null, "assert arg0 != null", "assert arg1 != null : arg0"});
    ourStatementMap.put("assertNull", new String[]{null, "assert arg0 == null", "assert arg1 == null : arg0"});

    ourStatementMap.put("assertTrue", new String[]{null, "assert arg0", "assert arg1 : arg0"});
    ourStatementMap.put("assertFalse", new String[]{null, "assert !arg0", "assert !arg1 : arg0"});

    ourStatementMap.put("assertEquals", new String[]{null, null, "assert arg0 == arg1", "assert arg1 == arg2 : arg0"});

    ourStatementMap.put("assertSame", new String[]{null, null, "assert arg0.is(arg1)", "assert arg1.is(arg2) : arg0"});
    ourStatementMap.put("assertNotSame", new String[]{null, null, "assert !arg0.is(arg1)", "assert !arg1.is(arg2) : arg0"});
  }
  
  @Nullable
  private static String getReplacementStatement(@Nonnull PsiMethod method, @Nonnull GrMethodCall methodCall) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return null;

    String qualifiedName = containingClass.getQualifiedName();
    if (!"junit.framework.Assert".equals(qualifiedName) && !"groovy.util.GroovyTestCase".equals(qualifiedName)) return null;

    String[] replacementStatements = ourStatementMap.get(method.getName());
    if (replacementStatements == null) return null;
    
    GrArgumentList argumentList = methodCall.getArgumentList();
    if (argumentList == null) return null;

    if (argumentList.getNamedArguments().length > 0) return null;

    GrExpression[] arguments = argumentList.getExpressionArguments();

    if (arguments.length >= replacementStatements.length) return null;
    
    return replacementStatements[arguments.length];
  }
  
  @Nullable
  private static GrStatement getReplacementElement(@Nonnull PsiMethod method, @Nonnull GrMethodCall methodCall) {
    String replacementStatement = getReplacementStatement(method, methodCall);
    if (replacementStatement == null) return null;
    
    @SuppressWarnings("ConstantConditions") final
    GrExpression[] arguments = methodCall.getArgumentList().getExpressionArguments();
    
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(method.getProject());
    
    GrAssertStatement statement = (GrAssertStatement)factory.createStatementFromText(replacementStatement);
    
    final Map<GrExpression, GrExpression> replaceMap = new HashMap<GrExpression, GrExpression>();
    
    statement.acceptChildren(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitExpression(GrExpression expression) {
        Matcher matcher = PATTERN.matcher(expression.getText());
        if (matcher.matches()) {
          int index = Integer.parseInt(matcher.group(1));
          replaceMap.put(expression, arguments[index]);
        }
        else {
          super.visitExpression(expression);
        }
      }
    });

    for (Map.Entry<GrExpression, GrExpression> entry : replaceMap.entrySet()) {
      entry.getKey().replaceWithExpression(entry.getValue(), true);
    }

    return statement;
  }

  @Override
  protected void processIntention(@Nonnull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    GrMethodCall methodCall = (GrMethodCall)element;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;

    GrStatement replacementElement = getReplacementElement(method, methodCall);
    if (replacementElement == null) return;

    ((GrMethodCall)element).replaceWithStatement(replacementElement);
  }

  @Nonnull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return this;
  }

  @Override
  public boolean satisfiedBy(PsiElement element) {
    if (!(element instanceof GrMethodCall)) return false;
    
    GrMethodCall methodCall = (GrMethodCall)element;

    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return false;

    return getReplacementStatement(method, methodCall) != null;
  }
}
