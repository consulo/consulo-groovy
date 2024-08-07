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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import consulo.language.ast.ASTNode;
import jakarta.annotation.Nonnull;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

/**
 * @author Dmitry.Krasilschikov
 * @date 26.03.2007
 */

public class GrMethodImpl extends GrMethodBaseImpl implements GrMethod {
  public GrMethodImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrMethodImpl(GrMethodStub stub) {
    super(stub, GroovyElementTypes.METHOD_DEFINITION);
  }

  @Override
  public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first == last && first.getPsi() instanceof GrTypeParameterList) {
      if (!getModifierList().hasExplicitVisibilityModifiers()) {
        getModifierList().setModifierProperty(GrModifier.DEF, true);
      }
    }
    return super.addInternal(first, last, anchor, before);
  }

  public String toString() {
    return "Method";
  }

}
