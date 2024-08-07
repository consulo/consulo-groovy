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

import consulo.language.psi.stub.*;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.collection.ArrayUtil;
import consulo.index.io.StringRef;
import jakarta.annotation.Nonnull;

import consulo.language.psi.stub.StubElement;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant.GrEnumConstantImpl;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrEnumConstantElementType extends GrStubElementType<GrFieldStub, GrEnumConstant> {

  public GrEnumConstantElementType() {
    super("Enumeration constant");
  }

  public GrEnumConstant createPsi(@Nonnull GrFieldStub stub) {
    return new GrEnumConstantImpl(stub);
  }

  @Override
  public GrFieldStub createStub(@Nonnull GrEnumConstant psi, StubElement parentStub) {
    String[] annNames = GrStubUtils.getAnnotationNames(psi);
    return new GrFieldStub(parentStub, StringRef.fromString(psi.getName()), annNames, ArrayUtil.EMPTY_STRING_ARRAY, GroovyElementTypes.ENUM_CONSTANT, GrFieldStub.buildFlags(psi), null);
  }

  public void serialize(@Nonnull GrFieldStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    serializeFieldStub(stub, dataStream);
  }

  @Nonnull
  public GrFieldStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return GrFieldElementType.deserializeFieldStub(dataStream, parentStub);
  }

  protected static void serializeFieldStub(GrFieldStub stub, StubOutputStream dataStream) throws IOException {
    GrFieldElementType.serializeFieldStub(stub, dataStream);
  }

  public void indexStub(@Nonnull GrFieldStub stub, @Nonnull IndexSink sink) {
    GrFieldElementType.indexFieldStub(stub, sink);
  }
}
