/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import java.io.IOException;

import javax.annotation.Nonnull;

import consulo.index.io.StringRef;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubOutputStream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.toplevel.packaging.GrPackageDefinitionImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrPackageDefinitionStub;
import consulo.language.psi.stub.StubInputStream;

public class GrPackageDefinitionElementType extends GrStubElementType<GrPackageDefinitionStub, GrPackageDefinition> {
  public GrPackageDefinitionElementType(@NonNls @Nonnull String debugName) {
    super(debugName);
  }

  @Override
  public GrPackageDefinition createPsi(@Nonnull GrPackageDefinitionStub stub) {
    return new GrPackageDefinitionImpl(stub);
  }

  @Override
  public GrPackageDefinitionStub createStub(@Nonnull GrPackageDefinition psi, StubElement parentStub) {
    return new GrPackageDefinitionStub(parentStub, GroovyElementTypes.PACKAGE_DEFINITION, StringRef.fromString(psi.getPackageName()));
  }

  @Override
  public void serialize(@Nonnull GrPackageDefinitionStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName());
  }

  @Nonnull
  @Override
  public GrPackageDefinitionStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrPackageDefinitionStub(parentStub, GroovyElementTypes.PACKAGE_DEFINITION, dataStream.readName());
  }
}
