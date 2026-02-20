/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.impl.dsl;

import consulo.application.Application;
import consulo.ide.impl.idea.notification.impl.NotificationsConfigurationImpl;
import consulo.logging.Logger;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.util.lang.ExceptionUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.dsl.DslActivationStatus;
import org.jetbrains.plugins.groovy.dsl.DslErrorReporter;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;

public class DslErrorReporterImpl extends DslErrorReporter {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex");
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Groovy DSL errors",
                                                                             NotificationDisplayType.BALLOON, true);
  public DslErrorReporterImpl() {
    NotificationsConfigurationImpl.remove("Groovy DSL parsing");
  }

  @Override
  public void invokeDslErrorPopup(Throwable e, Project project, @Nonnull VirtualFile vfile) {
    if (!GroovyDslFileIndex.isActivated(vfile)) {
      return;
    }

    String exceptionText = ExceptionUtil.getThrowableText(e);
    LOG.info(exceptionText);
    GroovyDslFileIndex.disableFile(vfile, DslActivationStatus.Status.ERROR, exceptionText);


    if (!Application.get().isInternal() && !ProjectRootManager.getInstance(project)
                                                        .getFileIndex().isInContent(vfile)) {
      return;
    }

    String content = "<p>" + e.getMessage() + "</p><p><a href=\"\">Click here to investigate.</a></p>";
    NOTIFICATION_GROUP.createNotification("DSL script execution error", content, NotificationType.ERROR, (notification, event) -> {
      InvestigateFix.analyzeStackTrace(project, exceptionText);
      notification.expire();
    }).notify(project);
  }
}
