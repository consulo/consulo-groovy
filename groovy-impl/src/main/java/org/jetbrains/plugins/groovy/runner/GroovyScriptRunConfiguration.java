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
package org.jetbrains.plugins.groovy.runner;

import com.intellij.java.execution.CommonJavaRunConfigurationParameters;
import com.intellij.java.execution.configurations.JavaCommandLineState;
import com.intellij.java.language.psi.PsiClass;
import consulo.application.ApplicationManager;
import consulo.component.macro.PathMacroManager;
import consulo.content.bundle.Sdk;
import consulo.execution.CantRunException;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.configuration.*;
import consulo.execution.configuration.ui.SettingsEditor;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.util.ProgramParametersUtil;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectRootManager;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.project.macro.ProjectPathMacroManager;
import consulo.util.io.FileUtil;
import consulo.util.lang.SystemProperties;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.JDOMExternalizer;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFilePathUtil;
import org.jdom.Element;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author peter
 */
public class GroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements CommonJavaRunConfigurationParameters, RefactoringListenerProvider {
  private static final Logger LOG = Logger.getInstance(GroovyScriptRunConfiguration.class);
  private String vmParams;
  private String workDir;
  private boolean isDebugEnabled;
  @Nullable
  private String scriptParams;
  @Nullable
  private String scriptPath;
  private final Map<String, String> envs = new LinkedHashMap<String, String>();
  public boolean passParentEnv = true;

  public GroovyScriptRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project), factory);
    workDir = VirtualFilePathUtil.getLocalPath(project.getBaseDir());
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(getName(), getProject(), getFactory());
  }

  public void setWorkDir(String dir) {
    workDir = dir;
  }

  public String getWorkDir() {
    return workDir;
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final GroovyScriptRunner scriptRunner = findConfiguration();
    if (scriptRunner == null) {
      return Arrays.asList(modules);
    }


    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (scriptRunner.isValidModule(module)) {
        res.add(module);
      }
    }
    return res;
  }

  @Nullable
  private GroovyScriptRunner findConfiguration() {
    final VirtualFile scriptFile = getScriptFile();
    if (scriptFile == null) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scriptFile);
    if (!(psiFile instanceof GroovyFile)) {
      return null;
    }

    final GroovyFile groovyFile = (GroovyFile)psiFile;
    if (groovyFile.isScript()) {
      return GroovyScriptUtil.getScriptType(groovyFile).getRunner();
    }
    else {
      return new DefaultGroovyScriptRunner();
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    ProjectPathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    scriptPath = ExternalizablePath.localPathValue(JDOMExternalizer.readString(element, "path"));
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    final String wrk = JDOMExternalizer.readString(element, "workDir");
    if (!".".equals(wrk)) {
      workDir = ExternalizablePath.localPathValue(wrk);
    }
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
    envs.clear();
    JDOMExternalizer.readMap(element, envs, null, "env");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizer.write(element, "path", ExternalizablePath.urlValue(scriptPath));
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", ExternalizablePath.urlValue(workDir));
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
    JDOMExternalizer.writeMap(element, envs, null, "env");
    ProjectPathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment environment) throws ExecutionException {
    final VirtualFile script = getScriptFile();
    if (script == null) {
      throw new CantRunException("Cannot find script " + scriptPath);
    }

    final GroovyScriptRunner scriptRunner = findConfiguration();
    if (scriptRunner == null) {
      throw new CantRunException("Unknown script type " + scriptPath);
    }

    final Module module = getModule();
    if (!scriptRunner.ensureRunnerConfigured(module, this, executor, getProject())) {
      return null;
    }

    final boolean tests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(script);

    final JavaCommandLineState state = new JavaCommandLineState(environment) {
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
              if (!ApplicationManager.getApplication().isDisposed()) {
                VirtualFileManager.getInstance().asyncRefresh(null);
              }
            }
          });
        }
      }

      protected OwnJavaParameters createJavaParameters() throws ExecutionException {
        OwnJavaParameters params = createJavaParametersWithSdk(module);
        ProgramParametersUtil.configureConfiguration(params, GroovyScriptRunConfiguration.this);
        scriptRunner.configureCommandLine(params, module, tests, script, GroovyScriptRunConfiguration.this);

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;
  }

  public void setScriptParameters(String scriptParameters) {
    scriptParams = scriptParameters;
  }

  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (scriptPath == null || !scriptPath.equals(getPathByElement(element))) {
      return null;
    }

    final PsiClass classToRun = GroovyRunnerUtil.getRunningClass(element);

    if (element instanceof GroovyFile) {
      return new RefactoringElementAdapter() {
        @Override
        protected void elementRenamedOrMoved(@Nonnull PsiElement newElement) {
          if (newElement instanceof GroovyFile) {
            GroovyFile file = (GroovyFile)newElement;
            setScriptPath(file.getVirtualFile().getPath());

            final PsiClass newClassToRun = GroovyRunnerUtil.getRunningClass(newElement);
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
        protected void elementRenamedOrMoved(@Nonnull PsiElement newElement) {
          setName(((PsiClass)newElement).getName());
        }

        @Override
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
    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) {
      return null;
    }
    return vfile.getPath();
  }

  public static OwnJavaParameters createJavaParametersWithSdk(@Nullable Module module) {
    OwnJavaParameters params = new OwnJavaParameters();
    params.setCharset(null);

    if (module != null) {
      final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
      if (sdk != null) {
        params.setJdk(sdk);
      }
    }
    return params;
  }

  @Nullable
  private VirtualFile getScriptFile() {
    if (scriptPath == null) {
      return null;
    }
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(scriptPath));
  }

  @Nullable
  private PsiClass getScriptClass() {
    final VirtualFile scriptFile = getScriptFile();
    if (scriptFile == null) {
      return null;
    }
    final PsiFile file = PsiManager.getInstance(getProject()).findFile(scriptFile);
    return GroovyRunnerUtil.getRunningClass(file);
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();
    final PsiClass toRun = getScriptClass();
    if (toRun == null) {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("class.does.not.exist"));
    }
    if (toRun instanceof GrTypeDefinition) {
      if (!GroovyRunnerUtil.canBeRunByGroovy(toRun)) {
        throw new RuntimeConfigurationWarning(GroovyBundle.message("class.can't be executed"));
      }
    }
    else if (!(toRun instanceof GroovyScriptClass)) {
      throw new RuntimeConfigurationWarning(GroovyBundle.message("script.file.is.not.groovy.file"));
    }
  }

  @Override
  public void setVMParameters(String value) {
    vmParams = value;
  }

  @Override
  public String getVMParameters() {
    return vmParams;
  }

  @Override
  public boolean isAlternativeJrePathEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAlternativeJrePathEnabled(boolean enabled) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getAlternativeJrePath() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAlternativeJrePath(String path) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getRunClass() {
    return null;
  }

  @Override
  public String getPackage() {
    return null;
  }

  @Override
  public void setProgramParameters(@Nullable String value) {
    LOG.error("Don't add program parameters to Groovy script run configuration. Use Script parameters instead");
  }

  @Override
  public String getProgramParameters() {
    return null;
  }

  @Nullable
  public String getScriptParameters() {
    return scriptParams;
  }

  @Override
  public void setWorkingDirectory(@Nullable String value) {
    workDir = value;
  }

  @Override
  public String getWorkingDirectory() {
    return workDir;
  }

  @Override
  public void setEnvs(@Nonnull Map<String, String> envs) {
    this.envs.clear();
    this.envs.putAll(envs);
  }

  @Nonnull
  @Override
  public Map<String, String> getEnvs() {
    return envs;
  }

  @Override
  public void setPassParentEnvs(boolean passParentEnvs) {
    this.passParentEnv = passParentEnvs;
  }

  @Override
  public boolean isPassParentEnvs() {
    return passParentEnv;
  }

  public boolean isDebugEnabled() {
    return isDebugEnabled;
  }

  public void setDebugEnabled(boolean debugEnabled) {
    isDebugEnabled = debugEnabled;
  }

  @Nullable
  public String getScriptPath() {
    return scriptPath;
  }

  public void setScriptPath(@Nullable String scriptPath) {
    this.scriptPath = scriptPath;
  }
}
