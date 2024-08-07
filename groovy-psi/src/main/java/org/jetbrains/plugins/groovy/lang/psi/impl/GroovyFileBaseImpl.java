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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.language.psi.PsiClass;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.util.IncorrectOperationException;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ControlFlowBuilder;

import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GroovyFileBaseImpl extends PsiFileBase implements GroovyFileBase, GrControlFlowOwner {

  private GrMethod[] myMethods = null;

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myMethods = null;
  }

  protected GroovyFileBaseImpl(FileViewProvider viewProvider, @Nonnull Language language) {
    super(viewProvider, language);
  }

  public GroovyFileBaseImpl(IFileElementType root, IFileElementType root1, FileViewProvider provider) {
    this(provider, root.getLanguage());
    init(root, root1);
  }

  @Override
  @Nonnull
  public FileType getFileType() {
    return GroovyFileType.GROOVY_FILE_TYPE;
  }

  public String toString() {
    return "Groovy script";
  }

  @Override
  @Nonnull
  public GrTypeDefinition[] getTypeDefinitions() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(TokenSets.TYPE_DEFINITIONS, GrTypeDefinition.ARRAY_FACTORY);
  }

  @Override
  @Nonnull
  public GrTopLevelDefinition[] getTopLevelDefinitions() {
    return findChildrenByClass(GrTopLevelDefinition.class);
  }

  @Override
  @Nonnull
  public GrMethod[] getCodeMethods() {
    final StubElement<?> stub = getStub();
    if (stub != null) {
      return stub.getChildrenByType(GroovyElementTypes.METHOD_DEFINITION, GrMethod.ARRAY_FACTORY);
    }

    return calcTreeElement().getChildrenAsPsiElements(GroovyElementTypes.METHOD_DEFINITION,
                                                      GrMethod.ARRAY_FACTORY);
  }

  @Nonnull
  @Override
  public GrMethod[] getMethods() {
    if (myMethods == null) {
      List<GrMethod> result = new ArrayList<GrMethod>();

      GrMethod[] methods = getCodeMethods();
      for (GrMethod method : methods) {
        final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
        if (reflectedMethods.length > 0) {
          result.addAll(Arrays.asList(reflectedMethods));
        }
        else {
          result.add(method);
        }
      }

      myMethods = result.toArray(new GrMethod[result.size()]);
    }
    return myMethods;
  }

  @Override
  @Nonnull
  public GrTopStatement[] getTopStatements() {
    return findChildrenByClass(GrTopStatement.class);
  }

  @Override
  public boolean importClass(PsiClass aClass) {
    return addImportForClass(aClass) != null;
  }

  @Override
  public void removeImport(@Nonnull GrImportStatement importStatement) throws IncorrectOperationException {
    GroovyCodeStyleManager.getInstance(getProject()).removeImport(this, importStatement);
  }

  @Override
  public void removeElements(PsiElement[] elements) throws IncorrectOperationException {
    for (PsiElement element : elements) {
      if (element.isValid()) {
        if (element.getParent() != this) {
          throw new IncorrectOperationException();
        }
        deleteChildRange(element, element);
      }
    }
  }

  @Nonnull
  @Override
  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  @Override
  @Nonnull
  public GrStatement addStatementBefore(@Nonnull GrStatement statement,
                                        @Nullable GrStatement anchor) throws IncorrectOperationException {
    final PsiElement result = addBefore(statement, anchor);
    if (anchor != null) {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", anchor.getNode());
    }
    else {
      getNode().addLeaf(GroovyTokenTypes.mNLS, "\n", result.getNode());
    }
    return (GrStatement)result;
  }

  @Override
  public void removeVariable(GrVariable variable) {
    PsiImplUtil.removeVariable(variable);
  }

  @Override
  public GrVariableDeclaration addVariableDeclarationBefore(GrVariableDeclaration declaration,
                                                            GrStatement anchor) throws IncorrectOperationException {
    GrStatement statement = addStatementBefore(declaration, anchor);
    assert statement instanceof GrVariableDeclaration;
    return ((GrVariableDeclaration)statement);
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitFile(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      if (child instanceof GroovyPsiElement) {
        ((GroovyPsiElement)child).accept(visitor);
      }

      child = child.getNextSibling();
    }
  }

  @Override
  @Nonnull
  public PsiClass[] getClasses() {
    return getTypeDefinitions();
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myControlFlow = null;
  }

  private volatile SoftReference<Instruction[]> myControlFlow = null;

  @Override
  public Instruction[] getControlFlow() {
    assert isValid();
    Instruction[] result = SoftReference.dereference(myControlFlow);
    if (result == null) {
      result = new ControlFlowBuilder(getProject()).buildControlFlow(this);
      myControlFlow = new SoftReference<Instruction[]>(result);
    }
    return ControlFlowBuilder.assertValidPsi(result);
  }

  @Override
  public boolean isTopControlFlowOwner() {
    return false;
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    if (last instanceof GrTopStatement) {
      PsiImplUtil.deleteStatementTail(this, last);
    }
    super.deleteChildRange(first, last);
  }

}
