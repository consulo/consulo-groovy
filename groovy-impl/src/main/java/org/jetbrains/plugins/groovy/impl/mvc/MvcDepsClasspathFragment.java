/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.execution.configuration.fragment.TagRunConfigurationFragment;
import consulo.groovy.localize.GroovyLocalize;
import consulo.localize.LocalizeValue;

/**
 * Passes the classpath of the application dependencies with {@code --classpath}.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class MvcDepsClasspathFragment implements TagRunConfigurationFragment<MvcRunConfiguration> {
  @Override
  public String getId() {
    return "mvc.deps.classpath";
  }

  @Override
  public LocalizeValue getName() {
    return GroovyLocalize.runConfigurationFragmentDepsClasspath();
  }

  @Override
  public boolean isSet(MvcRunConfiguration target) {
    return target.isDepsClasspath();
  }

  @Override
  public void set(MvcRunConfiguration target, boolean value) {
    target.setDepsClasspath(value);
  }
}
