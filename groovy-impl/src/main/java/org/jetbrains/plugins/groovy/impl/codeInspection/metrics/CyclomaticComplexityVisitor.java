/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.impl.codeInspection.metrics;

import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConditionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

class CyclomaticComplexityVisitor extends GroovyRecursiveElementVisitor {
  private int complexity = 1;

  public void visitElement(GroovyPsiElement GrElement) {
    int oldComplexity = 0;
    if (GrElement instanceof GrMethod) {
      oldComplexity = complexity;
    }
    super.visitElement(GrElement);

    if (GrElement instanceof GrMethod) {
      complexity = oldComplexity;
    }
  }

  public void visitForStatement(@Nonnull GrForStatement statement) {
    super.visitForStatement(statement);
    complexity++;
  }


  public void visitIfStatement(@Nonnull GrIfStatement statement) {
    super.visitIfStatement(statement);
    complexity++;
  }

  public void visitConditionalExpression(GrConditionalExpression expression) {
    super.visitConditionalExpression(expression);
    complexity++;
  }

  public void visitSwitchStatement(@Nonnull GrSwitchStatement statement) {
    super.visitSwitchStatement(statement);
    final GrCaseSection[] caseClauses = statement.getCaseSections();
    for (GrCaseSection clause : caseClauses) {
      final GrStatement[] statements = clause.getStatements();
      if (statements != null && statements.length != 0) {
        complexity++;
      }
    }
  }

  public void visitWhileStatement(@Nonnull GrWhileStatement statement) {
    super.visitWhileStatement(statement);
    complexity++;
  }

  public int getComplexity() {
    return complexity;
  }
}
