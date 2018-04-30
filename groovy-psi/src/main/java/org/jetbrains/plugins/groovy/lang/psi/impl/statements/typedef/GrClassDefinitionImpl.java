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

package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import com.intellij.lang.ASTNode;

/**
 * @author Dmitry.Krasilschikov
 * @date 16.03.2007
 */
public class GrClassDefinitionImpl extends GrTypeDefinitionImpl implements GrClassDefinition {

  public GrClassDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrClassDefinitionImpl(final GrTypeDefinitionStub stub) {
    super(stub, GroovyElementTypes.CLASS_DEFINITION);
  }

  public String toString() {
    return "Class definition";
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitClassDefinition(this);
  }
}