/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.refactoring;

import consulo.language.psi.PsiNamedElement;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.intellij.java.language.psi.PsiField;
import com.intellij.java.language.psi.PsiModifier;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class DefaultGroovyVariableNameValidator implements NameValidator {
  private final GroovyPsiElement myContext;
  private final Set<String> mySet = new HashSet<String>();

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context) {
    this(context, Collections.<String>emptyList(), true, false);
  }

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context, Collection<String> restrictedNames) {
    this(context, restrictedNames, true, false);
  }

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context,
                                            Collection<String> restrictedNames,
                                            boolean includeFields) {
    this(context, restrictedNames, includeFields, false);
  }

  public DefaultGroovyVariableNameValidator(GroovyPsiElement context,
                                            Collection<String> restrictedNames,
                                            final boolean includeFields,
                                            final boolean checkIntoInner) {
    myContext = context;
    mySet.addAll(restrictedNames);
    PropertyResolverProcessor processor = new PropertyResolverProcessor(null, myContext);
    ResolveUtil.treeWalkUp(myContext, processor, true);
    final GroovyResolveResult[] results = processor.getCandidates();
    for (GroovyResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiNamedElement && (includeFields || !(element instanceof PsiField))) {
        mySet.add(((PsiNamedElement)element).getName());
      }
    }

    GroovyPsiElement scope = PsiTreeUtil.getParentOfType(context, GrControlFlowOwner.class, GrMember.class);
    if (scope == null) scope = context;

    scope.accept(new GroovyRecursiveElementVisitor(){
      @Override
      public void visitVariable(GrVariable variable) {
        if (includeFields || !(variable instanceof PsiField)) {
          mySet.add(variable.getName());
        }
        super.visitVariable(variable);
      }

      @Override
      public void visitClosure(GrClosableBlock closure) {
        if (checkIntoInner) {
          super.visitClosure(closure);
        }
      }

      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        if (checkIntoInner && !typeDefinition.hasModifierProperty(PsiModifier.STATIC) ) {
          super.visitTypeDefinition(typeDefinition);
        }
      }
    });
  }

  public String validateName(String name, boolean increaseNumber) {
    if (!mySet.contains(name)) return name;
    if (increaseNumber) {
      int i = 1;
      //noinspection StatementWithEmptyBody
      for (; mySet.contains(name + i); i++) ;
      return name + i;
    }
    else {
      return "";
    }
  }

  public Project getProject() {
    return myContext.getProject();
  }
}
