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

import java.io.IOException;

import jakarta.annotation.Nonnull;

import consulo.index.io.StringRef;
import consulo.language.Language;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.*;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullScriptNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrScriptClassNameIndex;
import consulo.language.psi.stub.StubBuilder;
import consulo.language.psi.stub.IndexSink;
import consulo.language.psi.stub.StubOutputStream;
import consulo.language.psi.stub.IStubFileElementType;

/**
 * @author ilyas
 */
public class GrStubFileElementType extends IStubFileElementType<GrFileStub> {

  public GrStubFileElementType(Language language) {
    super(language);
  }

  public StubBuilder getBuilder() {
    return new DefaultStubBuilder() {
      protected StubElement createStubForFile(@Nonnull final PsiFile file) {
        if (file instanceof GroovyFile) {
          return new GrFileStub((GroovyFile)file);
        }

        return super.createStubForFile(file);
      }
    };
  }

  @Override
  public int getStubVersion() {
    return super.getStubVersion() + 17;
  }

  @Nonnull
  public String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void serialize(@Nonnull final GrFileStub stub, @Nonnull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName().toString());
    dataStream.writeBoolean(stub.isScript());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
  }

  @Nonnull
  @Override
  public GrFileStub deserialize(@Nonnull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    boolean isScript = dataStream.readBoolean();
    return new GrFileStub(name, isScript, GrStubUtils.readStringArray(dataStream));
  }

  public void indexStub(@Nonnull GrFileStub stub, @Nonnull IndexSink sink) {
    String name = stub.getName().toString();
    if (stub.isScript() && name != null) {
      sink.occurrence(GrScriptClassNameIndex.KEY, name);
      final String pName = GrStubUtils.getPackageName(stub);
      final String fqn = StringUtil.isEmpty(pName) ? name : pName + "." + name;
      sink.occurrence(GrFullScriptNameIndex.KEY, fqn.hashCode());
    }

    for (String anno : stub.getAnnotations()) {
      sink.occurrence(GrAnnotatedMemberIndex.KEY, anno);
    }
  }

}
