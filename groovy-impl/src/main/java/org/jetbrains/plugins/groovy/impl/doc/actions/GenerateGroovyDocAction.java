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
package org.jetbrains.plugins.groovy.impl.doc.actions;

import consulo.application.dumb.DumbAware;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithAsyncUpdate;
import consulo.ui.ex.action.coroutine.ActionSafeReadLock;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.groovy.impl.doc.GenerateGroovyDocDialog;
import org.jetbrains.plugins.groovy.impl.doc.GroovyDocConfiguration;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

public final class GenerateGroovyDocAction extends AnAction implements DumbAware, AnActionWithAsyncUpdate {
    private static final String INDEX_HTML = "index.html";

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        Module module = e.getRequiredData(Module.KEY);

        GroovyDocConfiguration configuration = new GroovyDocConfiguration();

        VirtualFile[] files = ModuleRootManager.getInstance(module).getContentRoots();
        if (files.length == 1) {
            configuration.INPUT_DIRECTORY = files[0].getPath();
        }

        GenerateGroovyDocDialog dialog = new GenerateGroovyDocDialog(project, configuration);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        generateGroovydoc(configuration, project);
    }

    @Override
    public Coroutine<?, ?> updateAsync(AnActionEvent e) {
        return ActionSafeReadLock.run(e, presentation -> {
            Module module = e.getData(Module.KEY);

            e.getPresentation().setEnabledAndVisible(module != null && LibrariesUtil.hasGroovySdk(module));
        }).toCoroutine();
    }

    private static void generateGroovydoc(GroovyDocConfiguration configuration, Project project) {
        /* TODO[VISTALL]
        Runnable groovyDocRun = () -> {
            Groovydoc groovydoc = new Groovydoc();
            groovydoc.setProject(new org.apache.tools.ant.Project());
            groovydoc.setDestdir(new File(configuration.OUTPUT_DIRECTORY));
            groovydoc.setPrivate(configuration.OPTION_IS_PRIVATE);
            groovydoc.setUse(configuration.OPTION_IS_USE);
            groovydoc.setWindowtitle(configuration.WINDOW_TITLE);

            Path path = new Path(new org.apache.tools.ant.Project());
            path.setPath(configuration.INPUT_DIRECTORY);
            groovydoc.setSourcepath(path);

            String packages = "";
            for (int i = 0; i < configuration.PACKAGES.length; i++) {
                String s = configuration.PACKAGES[i];
                if (s != null && s.isEmpty()) {
                    continue;
                }

                if (i > 0) {
                    packages += ",";
                }

                packages += s;
            }
            groovydoc.setPackagenames(packages);

            ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText(GroovyDocLocalize.groovyDocProgressIndicationText());
            groovydoc.execute();
        };

        ProgressManager.getInstance()
            .runProcessWithProgressSynchronously(groovyDocRun, GroovyDocLocalize.groovyDocumentationGenerating(), false, project);

        if (configuration.OPEN_IN_BROWSER) {
            File url = new File(configuration.OUTPUT_DIRECTORY, INDEX_HTML);
            if (url.exists()) {
                BrowserUtil.browse(url);
            }
        }*/
    }
}
