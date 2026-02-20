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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.IncorrectOperationException;

/**
 * @author ilyas
 */
public abstract class GrStubElementBase<T extends StubElement> extends StubBasedPsiElementBase<T> implements GroovyPsiElement {

  protected GrStubElementBase(T stub, IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public GrStubElementBase(ASTNode node) {
    super(node);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    getParent().deleteChildRange(this, this);
  }

  @Override
  public abstract PsiElement getParent();

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(GroovyElementVisitor visitor) {
    GroovyPsiElementImpl.acceptGroovyChildren(this, visitor);
  }

  protected PsiElement getDefinitionParent() {
    PsiElement candidate = getParentByStub();
    if (candidate instanceof GroovyFile || candidate instanceof GrTypeDefinitionBody) {
      return candidate;
    }

    return getParentByTree();
  }
}