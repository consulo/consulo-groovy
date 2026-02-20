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

import consulo.language.psi.stub.NamedStub;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.index.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;

/**
 * @author peter
 */
public class GrTypeParameterStub extends StubBase<GrTypeParameter> implements NamedStub<GrTypeParameter> {
  private final StringRef myName;

  public GrTypeParameterStub(StubElement parentStub, StringRef name) {
    super(parentStub, GroovyElementTypes.TYPE_PARAMETER);
    myName = name;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

}
