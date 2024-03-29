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
package org.jetbrains.plugins.groovy.impl.codeInspection;

import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.ide.ServiceManager;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.intention.IntentionAction;
import org.jetbrains.plugins.groovy.impl.lang.GrCreateClassKind;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

@ServiceAPI(ComponentScope.APPLICATION)
public abstract class GroovyQuickFixFactory {
  public static GroovyQuickFixFactory getInstance() {
    return ServiceManager.getService(GroovyQuickFixFactory.class);
  }

  public abstract IntentionAction createDynamicMethodFix(GrReferenceExpression expression, PsiType[] types);

  public abstract IntentionAction createDynamicPropertyFix(GrReferenceExpression expression);

  public abstract IntentionAction createGroovyAddImportAction(GrReferenceElement element);

  public abstract IntentionAction createClassFromNewAction(GrNewExpression parent);

  public abstract IntentionAction createClassFixAction(GrReferenceElement element, GrCreateClassKind anInterface);

  public abstract IntentionAction createCreateFieldFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateGetterFromUsageFix(GrReferenceExpression expr, PsiClass aClass);

  public abstract IntentionAction createCreateSetterFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateMethodFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createCreateLocalVariableFromUsageFix(GrReferenceExpression expr,
                                                                        GrVariableDeclarationOwner owner);

  public abstract IntentionAction createCreateParameterFromUsageFix(GrReferenceExpression expr);

  public abstract IntentionAction createGroovyStaticImportMethodFix(GrMethodCall parent);

  public abstract GroovyFix createRenameFix();

  public abstract GroovyFix createReplaceWithImportFix();

  public abstract LocalQuickFix createGrMoveToDirFix(String actual);

  public abstract LocalQuickFix createCreateFieldFromConstructorLabelFix(GrTypeDefinition element,
                                                                         GrNamedArgument argument);

  public abstract LocalQuickFix createDynamicPropertyFix(GrArgumentLabel label, PsiClass element);

  public abstract GroovyFix createAddMethodFix(String methodName, GrTypeDefinition aClass);

  public abstract GroovyFix createAddClassToExtendsFix(GrTypeDefinition aClass, String comparable);

  public abstract IntentionAction createOptimizeImportsFix(boolean onTheFly);

  public abstract IntentionAction createRemoveUnusedGrParameterFix(GrParameter parameter);

  public abstract IntentionAction createInvestigateFix(String reason);
}
