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
package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import javax.annotation.Nonnull;

/**
 * @author ilyas
 */
@ExtensionImpl
public class GrScriptClassNameIndex extends StringStubIndexExtension<GroovyFile> {
  public static final StubIndexKey<String, GroovyFile> KEY = StubIndexKey.createIndexKey("gr.script.class");

  @Nonnull
  public StubIndexKey<String, GroovyFile> getKey() {
    return KEY;
  }
}
