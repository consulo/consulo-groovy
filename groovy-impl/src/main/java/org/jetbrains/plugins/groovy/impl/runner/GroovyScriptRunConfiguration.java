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
package org.jetbrains.plugins.groovy.impl.runner;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.execution.impl.util.JavaParametersUtil;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.*;
import consulo.execution.configuration.fragment.EnvironmentFragmentState;
import consulo.execution.configuration.fragment.ProgramParametersFragmentState;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.WorkingDirectoryFragmentState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.util.ProgramParametersUtil;
import consulo.groovy.localize.GroovyLocalize;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.configurations.fragment.VmParametersFragmentState;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectRootManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.io.FileUtil;
import consulo.util.xml.serializer.JDOMExternalizer;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import java.util.*;

/**
 * @author peter
 */
public class GroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule>
    implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {

    public GroovyScriptRunConfiguration(String name, Project project, ConfigurationFactory factory) {
        super(name, new RunConfigurationModule(project), factory);
    }

    @Override
    protected Class<GroovyScriptRunConfigurationOptions> getOptionsClass() {
        return GroovyScriptRunConfigurationOptions.class;
    }

    @Override
    public GroovyScriptRunConfigurationOptions getOptions() {
        return (GroovyScriptRunConfigurationOptions) super.getOptions();
    }

    @Override
    protected void convertLegacyState(LegacyRunConfigurationConverter converter) {
        super.convertLegacyState(converter);

        Element element = converter.getElement();
        ProjectPathMacroManager.getInstance(getProject()).expandPaths(element);

        GroovyScriptRunConfigurationOptions options = new GroovyScriptRunConfigurationOptions();
        options.setScriptPath(ExternalizablePath.localPathValue(JDOMExternalizer.readString(element, "path")));
        options.setDebug(Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug")));
        converter.setOptions(options);

        String workDir = JDOMExternalizer.readString(element, "workDir");
        converter.addFragment(new ProgramParametersFragmentState(JDOMExternalizer.readString(element, "params")));
        converter.addFragment(new WorkingDirectoryFragmentState(".".equals(workDir) ? null : ExternalizablePath.localPathValue(workDir)));
        converter.addFragment(new EnvironmentFragmentState(converter.readEnvs(), true));
        converter.addFragment(new VmParametersFragmentState(JDOMExternalizer.readString(element, "vmparams")));
    }

    @Override
    public List<RunConfigurationFragment<?>> createFragments() {
        return List.of(new GroovyScriptPathFragment(), new GroovyScriptDebugFragment());
    }

    @Override
    protected ModuleBasedConfiguration createInstance() {
        return new GroovyScriptRunConfiguration(getName(), getProject(), getFactory());
    }

    public void setWorkDir(@Nullable String dir) {
        setWorkingDirectory(dir);
    }

    @Nullable
    public String getWorkDir() {
        return getWorkingDirectory();
    }

    @Nullable
    public Module getModule() {
        return getConfigurationModule().getModule();
    }

    @Override
    public Collection<Module> getValidModules() {
        Module[] modules = ModuleManager.getInstance(getProject()).getModules();
        GroovyScriptRunner scriptRunner = findConfiguration();
        if (scriptRunner == null) {
            return Arrays.asList(modules);
        }


        List<Module> res = new ArrayList<>();
        for (Module module : modules) {
            if (scriptRunner.isValidModule(module)) {
                res.add(module);
            }
        }
        return res;
    }

    @Nullable
    @RequiredReadAction
    private GroovyScriptRunner findConfiguration() {
        VirtualFile scriptFile = getScriptFile();
        if (scriptFile == null) {
            return null;
        }

        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scriptFile);
        if (!(psiFile instanceof GroovyFile groovyFile)) {
            return null;
        }

        if (groovyFile.isScript()) {
            return GroovyScriptUtil.getScriptType(groovyFile).getRunner();
        }
        else {
            return new DefaultGroovyScriptRunner();
        }
    }

    @Override
    @RequiredReadAction
    public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
        final VirtualFile script = getScriptFile();
        if (script == null) {
            throw new CantRunException(LocalizeValue.localizeTODO("Cannot find script " + getScriptPath()));
        }

        final GroovyScriptRunner scriptRunner = findConfiguration();
        if (scriptRunner == null) {
            throw new CantRunException(LocalizeValue.localizeTODO("Unknown script type " + getScriptPath()));
        }

        final Module module = getModule();
        if (!scriptRunner.ensureRunnerConfigured(module, this, executor, getProject())) {
            return null;
        }

        final boolean tests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(script);

        JavaCommandLineState state = new JavaCommandLineState(environment) {
            @Override
            protected void buildProcessHandler(@Nonnull ProcessHandlerBuilder builder) throws ExecutionException {
                super.buildProcessHandler(builder);
                builder.shouldDestroyProcessRecursively(true);
            }

            @Override
            protected void setupProcessHandler(@Nonnull ProcessHandler handler) {
                super.setupProcessHandler(handler);

                if (scriptRunner.shouldRefreshAfterFinish()) {
                    handler.addProcessListener(new ProcessListener() {
                        @Override
                        public void processTerminated(ProcessEvent event) {
                            if (!Application.get().isDisposed()) {
                                VirtualFileManager.getInstance().asyncRefresh(null);
                            }
                        }
                    });
                }
            }

            @Override
            protected OwnJavaParameters createJavaParameters() throws ExecutionException {
                OwnJavaParameters params = createJavaParametersWithSdk(module);
                String alternativeJrePath = getAlternativeJrePath();
                if (alternativeJrePath != null) {
                    params.setJdk(JavaParametersUtil.createProjectJdk(getProject(), alternativeJrePath));
                }
                configureEnvironment(params);
                scriptRunner.configureCommandLine(params, module, tests, script, GroovyScriptRunConfiguration.this);

                return params;
            }
        };

        state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
        return state;
    }

    /**
     * The working directory and the environment; the script parameters are appended after the script by the runner.
     */
    private void configureEnvironment(OwnJavaParameters params) {
        ProgramParametersUtil.configureConfiguration(params, this);
        params.getProgramParametersList().clearAll();
    }

    public void setScriptParameters(@Nullable String scriptParameters) {
        setProgramParameters(scriptParameters);
    }

    @Override
    public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
        String scriptPath = getScriptPath();
        if (scriptPath == null || !scriptPath.equals(getPathByElement(element))) {
            return null;
        }

        PsiClass classToRun = GroovyRunnerUtil.getRunningClass(element);

        if (element instanceof GroovyFile) {
            return new RefactoringElementAdapter() {
                @Override
                protected void elementRenamedOrMoved(@Nonnull PsiElement newElement) {
                    if (newElement instanceof GroovyFile file) {
                        setScriptPath(file.getVirtualFile().getPath());

                        PsiClass newClassToRun = GroovyRunnerUtil.getRunningClass(newElement);
                        if (newClassToRun instanceof GroovyScriptClass) {
                            setName(GroovyRunnerUtil.getConfigurationName(file.getScriptClass(), getConfigurationModule()));
                        }
                    }
                }

                @Override
                public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName) {
                    elementRenamedOrMoved(newElement);
                }
            };
        }
        else if (element instanceof PsiClass && element.getManager().areElementsEquivalent(element, classToRun)) {
            return new RefactoringElementAdapter() {
                @Override
                @RequiredReadAction
                protected void elementRenamedOrMoved(@Nonnull PsiElement newElement) {
                    setName(((PsiClass) newElement).getName());
                }

                @Override
                @RequiredReadAction
                public void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName) {
                    elementRenamedOrMoved(newElement);
                }
            };
        }
        return null;
    }

    @Nullable
    private static String getPathByElement(@Nonnull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }
        VirtualFile vFile = file.getVirtualFile();
        if (vFile == null) {
            return null;
        }
        return vFile.getPath();
    }

    public static OwnJavaParameters createJavaParametersWithSdk(@Nullable Module module) {
        OwnJavaParameters params = new OwnJavaParameters();
        params.setCharset(null);

        if (module != null) {
            Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
            if (sdk != null) {
                params.setJdk(sdk);
            }
        }
        return params;
    }

    @Nullable
    private VirtualFile getScriptFile() {
        String scriptPath = getScriptPath();
        if (scriptPath == null) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(scriptPath));
    }

    @Nullable
    @RequiredReadAction
    private PsiClass getScriptClass() {
        VirtualFile scriptFile = getScriptFile();
        if (scriptFile == null) {
            return null;
        }
        PsiFile file = PsiManager.getInstance(getProject()).findFile(scriptFile);
        return GroovyRunnerUtil.getRunningClass(file);
    }

    @Override
    @RequiredReadAction
    public void checkConfiguration() throws RuntimeConfigurationException {
        super.checkConfiguration();
        JavaParametersUtil.checkAlternativeJRE(this);
        PsiClass toRun = getScriptClass();
        if (toRun == null) {
            throw new RuntimeConfigurationWarning(GroovyLocalize.classDoesNotExist());
        }
        if (toRun instanceof GrTypeDefinition) {
            if (!GroovyRunnerUtil.canBeRunByGroovy(toRun)) {
                throw new RuntimeConfigurationWarning(GroovyLocalize.classCanTBeExecuted());
            }
        }
        else if (!(toRun instanceof GroovyScriptClass)) {
            throw new RuntimeConfigurationWarning(GroovyLocalize.scriptFileIsNotGroovyFile());
        }
    }

    @Override
    @Nullable
    public String getRunClass() {
        return null;
    }

    @Override
    @Nullable
    public String getPackage() {
        return null;
    }

    @Nullable
    public String getScriptParameters() {
        return getProgramParameters();
    }

    public boolean isDebugEnabled() {
        return getOptions().isDebug();
    }

    public void setDebugEnabled(boolean debugEnabled) {
        getOptions().setDebug(debugEnabled);
    }

    @Nullable
    public String getScriptPath() {
        return getOptions().getScriptPath();
    }

    public void setScriptPath(@Nullable String scriptPath) {
        getOptions().setScriptPath(scriptPath);
    }
}
