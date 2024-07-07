package org.jetbrains.plugins.groovy.impl.editor.actions.joinLines;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GrVariableJoinLinesHandler extends GrJoinLinesHandlerBase {
  @Override
  public int tryJoinStatements(@Nonnull GrStatement first, @Nonnull GrStatement second) {
    if (first instanceof GrVariableDeclaration && !((GrVariableDeclaration)first).isTuple() && second instanceof GrAssignmentExpression) {
      final GrExpression lvalue = ((GrAssignmentExpression)second).getLValue();
      final GrExpression rValue = ((GrAssignmentExpression)second).getRValue();

      if (lvalue instanceof GrReferenceExpression && rValue != null) {
        final PsiElement resolved = ((GrReferenceExpression)lvalue).resolve();
        if (ArrayUtil.contains(resolved, ((GrVariableDeclaration)first).getVariables())) {
          assert resolved instanceof GrVariable;
          if (((GrVariable)resolved).getInitializerGroovy() == null) {
            ((GrVariable)resolved).setInitializerGroovy(rValue);
            second.delete();
            GrExpression newInitializer = ((GrVariable)resolved).getInitializerGroovy();
            assert newInitializer != null;
            return newInitializer.getTextRange().getEndOffset();
          }
        }
      }
    }

    return CANNOT_JOIN;
  }
}
