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
package org.jetbrains.plugins.groovy.impl.codeInspection.utils;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getPropertyNameByGetterName;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.getPropertyNameBySetterName;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.isSimplePropertyGetter;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils.isSimplePropertySetter;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import com.intellij.java.language.psi.PsiMethod;

/**
 * @author Max Medvedev
 */
public class JavaStylePropertiesUtil {
  private static final Logger LOG = Logger.getInstance(JavaStylePropertiesUtil.class);

  public static void fixJavaStyleProperty(GrMethodCall call) {
    GrExpression invoked = call.getInvokedExpression();
    String accessorName = ((GrReferenceExpression)invoked).getReferenceName();
    if (isGetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      final GrExpression newCall = genRefForGetter(call, accessorName);
      call.replaceWithExpression(newCall, true);
    }
    else if (isSetterInvocation(call) && invoked instanceof GrReferenceExpression) {
      final GrStatement newCall = genRefForSetter(call, accessorName);
      call.replaceWithStatement(newCall);
    }
  }

  public static boolean isPropertyAccessor(GrMethodCall call) {
    return !isInvokedOnMap(call) && (isGetterInvocation(call) || isSetterInvocation(call));
  }

  private static GrAssignmentExpression genRefForSetter(GrMethodCall call, String accessorName) {
    String name = getPropertyNameBySetterName(accessorName);
    GrExpression value = call.getExpressionArguments()[0];
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    final GrAssignmentExpression assignment = (GrAssignmentExpression)factory.createStatementFromText(name + " = xxx", call);

    ((GrReferenceExpression)assignment.getLValue()).setQualifier(refExpr.getQualifier());
    assignment.getRValue().replaceWithExpression(value, true);

    return assignment;
  }

  private static GrExpression genRefForGetter(GrMethodCall call, String accessorName) {
    String name = getPropertyNameByGetterName(accessorName, true);
    GrReferenceExpression refExpr = (GrReferenceExpression)call.getInvokedExpression();
    String oldNameStr = refExpr.getReferenceNameElement().getText();
    String newRefExpr = StringUtil.trimEnd(refExpr.getText(), oldNameStr) + name;

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(call.getProject());
    return factory.createExpressionFromText(newRefExpr, call);
  }

  private static boolean isInvokedOnMap(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    return expr instanceof GrReferenceExpression && ResolveUtil.isKeyOfMap((GrReferenceExpression)expr);
  }

  private static boolean isSetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();

    if (!(expr instanceof GrReferenceExpression)) return false;
    GrReferenceExpression refExpr = (GrReferenceExpression)expr;

    PsiMethod method;
    if (call instanceof GrApplicationStatement) {
      PsiElement element = refExpr.resolve();
      if (!(element instanceof PsiMethod) || !isSimplePropertySetter(((PsiMethod)element))) return false;
      method = (PsiMethod)element;
    }
    else {
      method = call.resolveMethod();
      if (!isSimplePropertySetter(method)) return false;
      LOG.assertTrue(method != null);
    }

    if (!GroovyNamesUtil.isValidReference(getPropertyNameBySetterName(method.getName()), ((GrReferenceExpression)
			expr).getQualifier() != null, call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args == null || args.getExpressionArguments().length != 1 || PsiImplUtil.hasNamedArguments(args)) {
      return false;
    }

    GrAssignmentExpression assignment = genRefForSetter(call, refExpr.getReferenceName());
    GrExpression value = assignment.getLValue();
    if (value instanceof GrReferenceExpression &&
        call.getManager().areElementsEquivalent(((GrReferenceExpression)value).resolve(), method)) {
      return true;
    }

    return false;
  }

  private static boolean isGetterInvocation(GrMethodCall call) {
    GrExpression expr = call.getInvokedExpression();
    if (!(expr instanceof GrReferenceExpression)) return false;

    PsiMethod method = call.resolveMethod();
    if (!isSimplePropertyGetter(method)) return false;
    LOG.assertTrue(method != null);
    if (!GroovyNamesUtil.isValidReference(getPropertyNameByGetterName(method.getName(), true),
                                          ((GrReferenceExpression)expr).getQualifier() != null,
                                          call.getProject())) {
      return false;
    }

    GrArgumentList args = call.getArgumentList();
    if (args == null || args.getAllArguments().length != 0) {
      return false;
    }

    GrExpression ref = genRefForGetter(call, ((GrReferenceExpression)expr).getReferenceName());
    if (ref instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)ref).resolve();
      PsiManager manager = call.getManager();
      if (manager.areElementsEquivalent(resolved, method) || areEquivalentAccessors(method, resolved, manager)) {
        return true;
      }
    }

    return false;
  }

  private static boolean areEquivalentAccessors(PsiMethod method, PsiElement resolved, PsiManager manager) {
    if (!(resolved instanceof GrAccessorMethod) || !(method instanceof GrAccessorMethod)) {
      return false;
    }

    if (((GrAccessorMethod)resolved).isSetter() != ((GrAccessorMethod)method).isSetter()) return false;

    GrField p1 = ((GrAccessorMethod)resolved).getProperty();
    GrField p2 = ((GrAccessorMethod)method).getProperty();
    return manager.areElementsEquivalent(p1, p2);
  }
}
