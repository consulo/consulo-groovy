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
package org.jetbrains.plugins.groovy.impl.shell;

import com.intellij.java.language.projectRoots.JavaSdkType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkTypeId;
import consulo.execution.executor.Executor;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.console.language.AbstractConsoleRunnerWithHistory;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.execution.ui.console.language.ProcessBackedConsoleExecuteActionHandler;
import consulo.ide.impl.idea.execution.console.ConsoleHistoryControllerImpl;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.local.ProcessHandlerFactory;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.Messages;
import consulo.util.dataholder.Key;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.impl.console.BuildAndRestartConsoleAction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import java.util.List;
import java.util.function.Consumer;

public class GroovyShellRunnerImpl extends AbstractConsoleRunnerWithHistory<LanguageConsoleView> {
  private static final Logger LOG = Logger.getInstance(GroovyShellRunnerImpl.class);
  public static final Key<Boolean> GROOVY_SHELL_FILE = Key.create("GROOVY_SHELL_FILE");
  public static final String GROOVY_SHELL_EXECUTE = "Groovy.Shell.Execute";

  private final GroovyShellConfig myShellRunner;
  private final Module myModule;
  private final Consumer<Module> myStarter = new Consumer<Module>() {
    public void accept(Module module) {
      doRunShell(myShellRunner, module);
    }
  };
  private GeneralCommandLine myCommandLine;

  public GroovyShellRunnerImpl(@Nonnull String consoleTitle, @Nonnull GroovyShellConfig shellRunner, @Nonnull Module module) {
    super(module.getProject(), consoleTitle, shellRunner.getWorkingDirectory(module));
    myShellRunner = shellRunner;
    myModule = module;
  }

  @Override
  protected List<AnAction> fillToolBarActions(DefaultActionGroup toolbarActions,
                                              Executor defaultExecutor,
                                              RunContentDescriptor contentDescriptor) {
    BuildAndRestartConsoleAction rebuildAction =
      new BuildAndRestartConsoleAction(myModule, getProject(), defaultExecutor, contentDescriptor, myStarter);
    toolbarActions.add(rebuildAction);
    List<AnAction> actions = super.fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);
    actions.add(rebuildAction);
    return actions;
  }

  @Override
  protected LanguageConsoleView createConsoleView() {
    LanguageConsoleView res = new GroovyShellLanguageConsoleView(getProject(), getConsoleTitle());
    GroovyFileImpl file = (GroovyFileImpl)res.getFile();
    assert file.getContext() == null;
    file.putUserData(GROOVY_SHELL_FILE, Boolean.TRUE);
    file.setContext(myShellRunner.getContext(myModule));
    return res;
  }

  @Nonnull
  @Override
  protected ProcessHandler createProcessHandler() throws ExecutionException {
    OwnJavaParameters javaParameters = myShellRunner.createJavaParameters(myModule);

    Sdk sdk = ModuleUtilCore.getSdk(myModule, JavaModuleExtension.class);
    assert sdk != null;
    SdkTypeId sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    javaParameters.setJdk(sdk);
    myCommandLine = OwnJdkUtil.setupJVMCommandLine(javaParameters);
    return ProcessHandlerFactory.getInstance().createProcessHandler(myCommandLine);
  }

  @Nonnull
  @Override
  protected ProcessBackedConsoleExecuteActionHandler createExecuteActionHandler() {
    ProcessBackedConsoleExecuteActionHandler handler =
      new ProcessBackedConsoleExecuteActionHandler(getProcessHandler(), false) {
        @Override
        public String getEmptyExecuteAction() {
          return GROOVY_SHELL_EXECUTE;
        }
      };
    new ConsoleHistoryControllerImpl(getConsoleTitle(), null, getConsoleView()).install();
    return handler;
  }

  public static void doRunShell(GroovyShellConfig config, Module module) {
    try {
      new GroovyShellRunnerImpl(config.getTitle(), config, module).initAndRun();
    }
    catch (ExecutionException e) {
      LOG.info(e);
      Messages.showErrorDialog(module.getProject(), e.getMessage(), "Cannot Run " + config.getTitle());
    }
  }
}