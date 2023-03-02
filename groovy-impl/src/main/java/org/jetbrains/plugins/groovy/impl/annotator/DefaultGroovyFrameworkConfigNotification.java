/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.annotator;

import consulo.annotation.component.ExtensionImpl;
import consulo.content.library.Library;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.GroovyFrameworkConfigNotification;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * @author sergey.evdokimov
 */
@ExtensionImpl(order = "last")
public class DefaultGroovyFrameworkConfigNotification extends GroovyFrameworkConfigNotification {

  @Override
  public boolean hasFrameworkStructure(@Nonnull Module module) {
    return true;
  }

  @Override
  public boolean hasFrameworkLibrary(@Nonnull Module module) {
    final Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module);
    return libraries.length > 0;
  }

  @Nullable
  @Override
  public EditorNotificationBuilder createConfigureNotificationPanel(@Nonnull Module module, Supplier<EditorNotificationBuilder> factory) {
    EditorNotificationBuilder builder = factory.get();
    builder.withText(LocalizeValue.localizeTODO(GroovyBundle.message("groovy.library.is.not.configured.for.module", module.getName())));
    builder.withAction(LocalizeValue.localizeTODO(GroovyBundle.message("configure.groovy.library")), uiEvent -> {
       /*AddFrameworkSupportDialog dialog = AddFrameworkSupportDialog.createDialog(module);
        if (dialog != null) {
          dialog.show();
        }*/
    });
    return builder;
  }
}
