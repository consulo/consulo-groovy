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

import consulo.application.util.CachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.LanguageCachedValueUtil;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrReflectedMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

import jakarta.annotation.Nonnull;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrConstructorImpl extends GrMethodBaseImpl implements GrMethod {
  public GrConstructorImpl(@Nonnull ASTNode node) {
    super(node);
  }

  public GrConstructorImpl(GrMethodStub stub) {
    super(stub, GroovyElementTypes.CONSTRUCTOR_DEFINITION);
  }

  public String toString() {
    return "Constructor";
  }

  @Override
  public boolean isConstructor() {
    return true;
  }

  @Nonnull
  @Override
  public GrReflectedMethod[] getReflectedMethods() {
    return LanguageCachedValueUtil.getCachedValue(this,
                                                  () -> CachedValueProvider.Result.create(GrReflectedMethodImpl.createReflectedConstructors(
                                                    GrConstructorImpl.this), PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
  }
}
