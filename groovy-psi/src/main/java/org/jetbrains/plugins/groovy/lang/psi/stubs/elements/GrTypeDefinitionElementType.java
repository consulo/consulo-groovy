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

import com.intellij.java.language.impl.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.java.language.psi.PsiNameHelper;
import consulo.index.io.StringRef;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.stub.StubInputStream;
import consulo.language.psi.stub.StubOutputStream;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullClassNameIndex;

import jakarta.annotation.Nonnull;
import java.io.IOException;

/**
 * @author ilyas
 */
public abstract class GrTypeDefinitionElementType<TypeDef extends GrTypeDefinition>
  extends GrStubElementType<GrTypeDefinitionStub, TypeDef> {

  public GrTypeDefinitionElementType(@Nonnull String debugName) {
    super(debugName);
  }

  public GrTypeDefinitionStub createStub(@Nonnull TypeDef psi, StubElement parentStub) {
    String[] superClassNames = psi.getSuperClassNames();
    final byte flags = GrTypeDefinitionStub.buildFlags(psi);
    return new GrTypeDefinitionStub(parentStub, psi.getName(), superClassNames, this, psi.getQualifiedName(), GrStubUtils
      .getAnnotationNames(psi),
                                        flags);
  }

  public void serialize(@Nonnull GrTypeDefinitionStub stub, @Nonnull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    dataStream.writeName(stub.getQualifiedName());
    dataStream.writeByte(stub.getFlags());
    writeStringArray(dataStream, stub.getSuperClassNames());
    writeStringArray(dataStream, stub.getAnnotations());
  }

  private static void writeStringArray(StubOutputStream dataStream, String[] names) throws IOException {
    dataStream.writeByte(names.length);
    for (String name : names) {
      dataStream.writeName(name);
    }
  }

  @Nonnull
  public GrTypeDefinitionStub deserialize(@Nonnull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = StringRef.toString(dataStream.readName());
    String qname = StringRef.toString(dataStream.readName());
    byte flags = dataStream.readByte();
    String[] superClasses = readStringArray(dataStream);
    String[] annos = readStringArray(dataStream);
    return new GrTypeDefinitionStub(parentStub, name, superClasses, this, qname, annos, flags);
  }

  private static String[] readStringArray(StubInputStream dataStream) throws IOException {
    byte supersNumber = dataStream.readByte();
    String[] superClasses = new String[supersNumber];
    for (int i = 0; i < supersNumber; i++) {
      superClasses[i] = StringRef.toString(dataStream.readName());
    }
    return superClasses;
  }

  public void indexStub(@Nonnull GrTypeDefinitionStub stub, @Nonnull IndexSink sink) {
    if (stub.isAnonymous()) {
      final String[] classNames = stub.getSuperClassNames();
      if (classNames.length != 1) return;
      final String baseClassName = classNames[0];
      if (baseClassName != null) {
        final String shortName = PsiNameHelper.getShortClassName(baseClassName);
        sink.occurrence(GrAnonymousClassIndex.KEY, shortName);
      }
    }
    else {
      String shortName = stub.getName();
      if (shortName != null) {
        sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, shortName);
      }
      final String fqn = stub.getQualifiedName();
      if (fqn != null) {
        sink.occurrence(GrFullClassNameIndex.KEY, fqn.hashCode());
        sink.occurrence(JavaStubIndexKeys.CLASS_FQN, fqn.hashCode());
      }
    }

    for (String annName : stub.getAnnotations()) {
      if (annName != null) {
        sink.occurrence(GrAnnotatedMemberIndex.KEY, annName);
      }
    }
  }
}
