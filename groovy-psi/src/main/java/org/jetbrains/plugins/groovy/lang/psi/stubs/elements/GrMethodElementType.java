/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import consulo.index.io.StringRef;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import consulo.util.collection.ArrayUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrMethodNameIndex;

import jakarta.annotation.Nonnull;
import java.io.IOException;
import java.util.Set;

/**
 * @author ilyas
 */
public abstract class GrMethodElementType extends GrStubElementType<GrMethodStub, GrMethod> {

  public GrMethodElementType(String debugName) {
    super(debugName);
  }

  public GrMethodStub createStub(@Nonnull GrMethod psi, StubElement parentStub) {

    Set<String> namedParameters = psi.getNamedParameters().keySet();
    return new GrMethodStub(parentStub, StringRef.fromString(psi.getName()), GrStubUtils.getAnnotationNames(psi),
                            ArrayUtil.toStringArray(namedParameters), this,
                            GrStubUtils.getTypeText(psi.getReturnTypeElementGroovy()),
                            GrMethodStub.buildFlags(psi));
  }

  public void serialize(@Nonnull GrMethodStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
    GrStubUtils.writeStringArray(dataStream, stub.getNamedParameters());
    GrStubUtils.writeNullableString(dataStream, stub.getTypeText());
    dataStream.writeByte(stub.getFlags());
  }

  @Nonnull
  public GrMethodStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    StringRef ref = dataStream.readName();
    String[] annNames = GrStubUtils.readStringArray(dataStream);
    String[] namedParameters = GrStubUtils.readStringArray(dataStream);
    String typeText = GrStubUtils.readNullableString(dataStream);
    byte flags = dataStream.readByte();
    return new GrMethodStub(parentStub, ref, annNames, namedParameters, this, typeText, flags);
  }

  public void indexStub(@Nonnull GrMethodStub stub, @Nonnull IndexSink sink) {
    String name = stub.getName();
    sink.occurrence(GrMethodNameIndex.KEY, name);
    if (GrStubUtils.isGroovyStaticMemberStub(stub)) {
      sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_NAMES, name);
      sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, GrStubUtils.getShortTypeText(stub.getTypeText()));
    }
    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
