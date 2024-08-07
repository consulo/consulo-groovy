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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.index.io.StringRef;
import consulo.language.psi.stub.NamedStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrMethodElementType;

import jakarta.annotation.Nonnull;

/**
 * @author ilyas
 */
public class GrMethodStub extends StubBase<GrMethod> implements NamedStub<GrMethod>
{
  public static final byte IS_DEPRECATED_BY_DOC_TAG = 0x01;

  private final StringRef myName;
  private final String[] myAnnotations;
  private final String[] myNamedParameters;
  private final String myTypeText;
  private final byte myFlags;

  public GrMethodStub(StubElement parent,
                      StringRef name,
                      final String[] annotations,
                      final @Nonnull String[] namedParameters,
                      final GrMethodElementType elementType,
                      @Nullable String typeText,
                      byte flags) {
    super(parent, elementType);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
    myTypeText = typeText;
    myFlags = flags;
  }

  @Nonnull
  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  @Nonnull
  public String[] getNamedParameters() {
    return myNamedParameters;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }

  public boolean isDeprecatedByDoc() {
    return (myFlags & IS_DEPRECATED_BY_DOC_TAG) != 0;
  }

  public static byte buildFlags(GrMethod method) {
    byte f = 0;

    if (PsiImplUtil.isDeprecatedByDocTag(method)) {
      f|= IS_DEPRECATED_BY_DOC_TAG;
    }

    return f;
  }

  public byte getFlags() {
    return myFlags;
  }
}
