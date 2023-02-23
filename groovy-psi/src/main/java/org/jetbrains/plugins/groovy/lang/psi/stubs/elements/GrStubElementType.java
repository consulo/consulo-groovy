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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import javax.annotation.Nonnull;

import consulo.language.psi.stub.IStubElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;

/**
 * @author ilyas
 */
public abstract class GrStubElementType<S extends StubElement, T extends GroovyPsiElement> extends IStubElementType<S, T>
{

  public GrStubElementType(@NonNls @Nonnull String debugName) {
    super(debugName, GroovyFileType.GROOVY_LANGUAGE);
  }

  public void indexStub(@Nonnull final S stub, @Nonnull final IndexSink sink) {
  }

  @Nonnull
  public String getExternalId() {
    return "gr." + super.toString();
  }

}
