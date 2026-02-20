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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.java.language.impl.psi.impl.PsiImplUtil;
import consulo.index.io.StringRef;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.NamedStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ilyas
 */
public class GrFieldStub extends StubBase<GrField> implements NamedStub<GrField> {
  public static final byte IS_PROPERTY = 0x01;
  public static final byte IS_ENUM_CONSTANT = 0x02;
  public static final byte IS_DEPRECATED_BY_DOC_TAG = 0x04;

  private final byte myFlags;
  private final StringRef myName;
  private final String[] myAnnotations;
  private final String[] myNamedParameters;
  private final String myTypeText;

  public GrFieldStub(StubElement parent,
                         StringRef name,
                         String[] annotations,
                         String[] namedParameters,
                         IStubElementType elemType,
                         byte flags, @Nullable String typeText) {
    super(parent, elemType);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
    myFlags = flags;
    myTypeText = typeText;
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

  public boolean isProperty() {
    return (myFlags & IS_PROPERTY) != 0;
  }

  public boolean isDeprecatedByDocTag() {
    return (myFlags & IS_DEPRECATED_BY_DOC_TAG) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }

  public static byte buildFlags(GrField field) {
    byte f = 0;
    if (field instanceof GrEnumConstant) {
      f |= IS_ENUM_CONSTANT;
    }

    if (field.isProperty()) {
      f |= IS_PROPERTY;
    }

    if (PsiImplUtil.isDeprecatedByDocTag(field)) {
      f|= IS_DEPRECATED_BY_DOC_TAG;
    }
    return f;
  }

  public static boolean isEnumConstant(byte flags) {
    return (flags & IS_ENUM_CONSTANT) != 0;
  }
}
