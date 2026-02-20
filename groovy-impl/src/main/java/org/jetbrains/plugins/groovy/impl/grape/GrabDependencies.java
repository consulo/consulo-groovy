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
package org.jetbrains.plugins.groovy.impl.grape;

import com.intellij.java.language.psi.JavaPsiFacade;
import consulo.application.WriteAction;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.codeEditor.Editor;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.execution.CantRunException;
import consulo.ide.impl.idea.openapi.module.ModuleUtil;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiRecursiveElementWalkingVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.local.ProcessHandlerFactory;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.ex.awt.Messages;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ExceptionUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.PathsList;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.grape.GrapeRunner;
import org.jetbrains.plugins.groovy.impl.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

/**
 * @author peter
 */
public class GrabDependencies implements IntentionAction {
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.grape.GrabDependencies");

    private static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Grape", NotificationDisplayType.BALLOON, true);

    @Nonnull
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO("Grab the artifacts");
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        GrAnnotation anno =
            PsiTreeUtil.findElementOfClassAtOffset(file, editor.getCaretModel().getOffset(), GrAnnotation.class, false);
        if (anno == null) {
            return false;
        }

        String qname = anno.getQualifiedName();
        if (qname == null || !(qname.startsWith(GrabAnnos.GRAB_ANNO) || GrabAnnos.GRAPES_ANNO.equals(qname))) {
            return false;
        }

        Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module == null) {
            return false;
        }

        Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
        if (sdk == null) {
            return false;
        }

        return file.getOriginalFile().getVirtualFile() != null;
    }

    public void invoke(@Nonnull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final Module module = ModuleUtil.findModuleForPsiElement(file);
        assert module != null;

        VirtualFile vfile = file.getOriginalFile().getVirtualFile();
        assert vfile != null;

        if (JavaPsiFacade.getInstance(project).findClass("org.apache.ivy.core.report.ResolveReport", file.getResolveScope()) == null) {
            Messages.showErrorDialog(
                "Sorry, but IDEA cannot @Grab the dependencies without Ivy. Please add Ivy to your module dependencies and re-run the action.",
                "Ivy Missing"
            );
            return;
        }

        Map<String, String> queries = prepareQueries(file);

        Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
        assert sdk != null;

        final Map<String, GeneralCommandLine> lines = new HashMap<String, GeneralCommandLine>();
        for (String grabText : queries.keySet()) {
            OwnJavaParameters javaParameters = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
            //debug
            //javaParameters.getVMParametersList().add("-Xdebug"); javaParameters.getVMParametersList().add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

            DefaultGroovyScriptRunner.configureGenericGroovyRunner(
                javaParameters,
                module,
                "org.jetbrains.plugins.groovy.grape.GrapeRunner",
                false,
                false
            );
            PathsList list;
            try {
                list = GroovyScriptRunner.getClassPathFromRootModel(
                    module,
                    ProjectRootManager.getInstance(project)
                        .getFileIndex()
                        .isInTestSourceContent(vfile),
                    javaParameters,
                    true
                );
            }
            catch (CantRunException e) {
                NOTIFICATION_GROUP.createNotification(
                    "Can't run @Grab: " + ExceptionUtil.getMessage(e),
                    ExceptionUtil.getThrowableText(e),
                    NotificationType.ERROR,
                    null
                ).notify(project);
                return;
            }
            if (list == null) {
                list = new PathsList();
            }
            list.add(PathUtil.getJarPathForClass(GrapeRunner.class));

            javaParameters.getProgramParametersList().add("--classpath");
            javaParameters.getProgramParametersList().add(list.getPathsString());
            javaParameters.getProgramParametersList().add(queries.get(grabText));

            javaParameters.setJdk(sdk);
            try {
                lines.put(grabText, OwnJdkUtil.setupJVMCommandLine(javaParameters));
            }
            catch (CantRunException e) {
                throw new IncorrectOperationException(e);
            }
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing @Grab annotations") {
            public void run(@Nonnull ProgressIndicator indicator) {
                int jarCount = 0;
                String messages = "";

                for (Map.Entry<String, GeneralCommandLine> entry : lines.entrySet()) {
                    String grabText = entry.getKey();
                    indicator.setText2(grabText);
                    try {
                        GrapeProcessHandler handler = new GrapeProcessHandler(entry.getValue(), module);
                        handler.startNotify();
                        handler.waitFor();
                        jarCount += handler.jarCount;
                        messages += "<b>" + grabText + "</b>: " + handler.messages + "<p>";
                    }
                    catch (ExecutionException e) {
                        LOG.error(e);
                    }
                }

                String finalMessages = messages;
                String title = jarCount + " Grape dependency jar" + (jarCount == 1 ? "" : "s") + " added";
                NOTIFICATION_GROUP.createNotification(title, finalMessages, NotificationType.INFORMATION, null)
                    .notify(project);
            }
        });


    }

    static Map<String, String> prepareQueries(PsiFile file) {
        final Set<GrAnnotation> grabs = new LinkedHashSet<GrAnnotation>();
        final Set<GrAnnotation> excludes = new HashSet<GrAnnotation>();
        final Set<GrAnnotation> resolvers = new HashSet<GrAnnotation>();
        file.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof GrAnnotation) {
                    GrAnnotation anno = (GrAnnotation) element;
                    String qname = anno.getQualifiedName();
                    if (GrabAnnos.GRAB_ANNO.equals(qname)) {
                        grabs.add(anno);
                    }
                    else if (GrabAnnos.GRAB_EXCLUDE_ANNO.equals(qname)) {
                        excludes.add(anno);
                    }
                    else if (GrabAnnos.GRAB_RESOLVER_ANNO.equals(qname)) {
                        resolvers.add(anno);
                    }
                }
                super.visitElement(element);
            }
        });

        Function<GrAnnotation, String> mapper = PsiElement::getText;
        String common = StringUtil.join(excludes, mapper, " ") + " " + StringUtil.join(resolvers, mapper, " ");
        LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
        for (GrAnnotation grab : grabs) {
            String grabText = grab.getText();
            result.put(grabText, (grabText + " " + common).trim());
        }
        return result;
    }

    public boolean startInWriteAction() {
        return false;
    }

    private static class GrapeProcessHandler implements ProcessListener {
        private final StringBuilder myStdOut = new StringBuilder();
        private final StringBuilder myStdErr = new StringBuilder();
        private final Module myModule;

        private final ProcessHandler myProcessHandler;

        public GrapeProcessHandler(GeneralCommandLine commandLine, Module module) throws ExecutionException {
            myProcessHandler = ProcessHandlerFactory.getInstance().createProcessHandler(commandLine);
            myProcessHandler.addProcessListener(this);
            myModule = module;
        }

        public void startNotify() {
            myProcessHandler.startNotify();
        }

        public void waitFor() {
            myProcessHandler.waitFor();
        }

        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
            String text = event.getText();
            text = StringUtil.convertLineSeparators(text);
            if (LOG.isDebugEnabled()) {
                LOG.debug(outputType + text);
            }
            if (outputType == ProcessOutputTypes.STDOUT) {
                myStdOut.append(text);
            }
            else if (outputType == ProcessOutputTypes.STDERR) {
                myStdErr.append(text);
            }
        }

        private void addGrapeDependencies(List<VirtualFile> jars) {
            ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
            LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
            for (VirtualFile jar : jars) {
                VirtualFile jarRoot = ArchiveVfsUtil.getJarRootForLocalFile(jar);
                if (jarRoot != null) {
                    OrderRootType rootType = BinariesOrderRootType.getInstance();
                    String libName = "Grab:" + jar.getName();
                    for (String classifier : List.of("sources", "source", "src")) {
                        if (libName.endsWith("-" + classifier + ".jar")) {
                            rootType = SourcesOrderRootType.getInstance();
                            libName = StringUtil.trimEnd(libName, "-" + classifier + ".jar") + ".jar";
                        }
                    }

                    Library library = tableModel.getLibraryByName(libName);
                    if (library == null) {
                        library = tableModel.createLibrary(libName);
                    }

                    Library.ModifiableModel libModel = library.getModifiableModel();
                    for (String url : libModel.getUrls(rootType)) {
                        libModel.removeRoot(url, rootType);
                    }
                    libModel.addRoot(jarRoot, rootType);
                    libModel.commit();
                }
            }
            tableModel.commit();
            model.commit();
        }

        int jarCount;
        String messages = "";

        @Override
        public void processTerminated(ProcessEvent event) {
            List<VirtualFile> jars = new ArrayList<VirtualFile>();
            for (String line : myStdOut.toString().split("\n")) {
                if (line.startsWith(GrapeRunner.URL_PREFIX)) {
                    try {
                        URL url = new URL(line.substring(GrapeRunner.URL_PREFIX.length()));
                        File libFile = new File(url.toURI());
                        if (libFile.exists() && libFile.getName().endsWith(".jar")) {
                            ContainerUtil.addIfNotNull(jars, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libFile));
                        }
                    }
                    catch (MalformedURLException | URISyntaxException e) {
                        LOG.error(e);
                    }
                }
            }
            WriteAction.run(() ->
            {
                jarCount = jars.size();
                messages = jarCount + " jar";
                if (jarCount != 1) {
                    messages += "s";
                }
                if (jarCount == 0) {
                    messages +=
                        "<br>" + myStdOut.toString().replaceAll("\n", "<br>") + "<p>" + myStdErr.toString().replaceAll("\n", "<br>");
                }
                if (!jars.isEmpty()) {
                    addGrapeDependencies(jars);
                }
            });
        }
    }
}
