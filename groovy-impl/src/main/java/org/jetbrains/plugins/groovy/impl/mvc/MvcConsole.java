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

package org.jetbrains.plugins.groovy.impl.mvc;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.document.FileDocumentManager;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.ui.console.ConsoleViewContentType;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.ui.layout.PlaceInGrid;
import consulo.execution.ui.layout.RunnerLayoutUi;
import consulo.execution.ui.layout.RunnerLayoutUiFactory;
import consulo.ide.ServiceManager;
import consulo.ide.impl.idea.execution.impl.ConsoleViewImpl;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessAdapter;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.local.ProcessHandlerFactory;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowManager;
import consulo.ui.ModalityState;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.Messages;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.content.ContentManager;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.util.collection.Lists;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.LocalFileSystem;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.JetgroovyIcons;

import javax.swing.*;
import java.awt.*;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class MvcConsole implements Disposable {
  private static final Key<Boolean> UPDATING_BY_CONSOLE_PROCESS = Key.create("UPDATING_BY_CONSOLE_PROCESS");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcConsole");
  private final ConsoleViewImpl myConsole;
  private final Project myProject;
  private final ToolWindow myToolWindow;
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final Queue<MyProcessInConsole> myProcessQueue = new LinkedList<MyProcessInConsole>();

  @NonNls
  private static final String CONSOLE_ID = "Groovy MVC Console";

  @NonNls
  public static final String TOOL_WINDOW_ID = "Console";

  private final MyKillProcessAction myKillAction = new MyKillProcessAction();
  private boolean myExecuting = false;
  private final Content myContent;

  @Inject
  public MvcConsole(Project project, TextConsoleBuilderFactory consoleBuilderFactory) {
    myProject = project;
    myConsole = (ConsoleViewImpl)consoleBuilderFactory.createBuilder(myProject).getConsole();
    Disposer.register(this, myConsole);

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.BOTTOM, this, true);
    myToolWindow.setIcon(JetgroovyIcons.Groovy.Groovy_13x13);

    myContent = setUpToolWindow();
  }

  public static MvcConsole getInstance(@Nonnull Project project) {
    return ServiceManager.getService(project, MvcConsole.class);
  }

  public static boolean isUpdatingVfsByConsoleProcess(@Nonnull Module module) {
    Boolean flag = module.getUserData(UPDATING_BY_CONSOLE_PROCESS);
    return flag != null && flag;
  }

  private Content setUpToolWindow() {
    //Create runner UI layout
    final RunnerLayoutUiFactory factory = RunnerLayoutUiFactory.getInstance(myProject);
    final RunnerLayoutUi layoutUi = factory.create("", "", "session", myProject);

    // Adding actions
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(myKillAction);
    group.addSeparator();

    layoutUi.getOptions().setLeftToolbar(group, ActionPlaces.UNKNOWN);

    final Content console = layoutUi.createContent(CONSOLE_ID, myConsole.getComponent(), "", null, null);
    layoutUi.addContent(console, 0, PlaceInGrid.right, false);

    final JComponent uiComponent = layoutUi.getComponent();
    myPanel.add(uiComponent, BorderLayout.CENTER);

    final ContentManager manager = myToolWindow.getContentManager();
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
    final Content content = contentFactory.createContent(uiComponent, null, true);
    manager.addContent(content);
    return content;
  }

  public void show(@Nullable final Runnable runnable, boolean focus) {
    Runnable r = null;
    if (runnable != null) {
      r = new Runnable() {
        public void run() {
          if (myProject.isDisposed()) {
            return;
          }

          runnable.run();
        }
      };
    }

    myToolWindow.activate(r, focus);
  }

  private static class MyProcessInConsole implements ConsoleProcessDescriptor {
    final Module module;
    final GeneralCommandLine commandLine;
    final
    @Nullable
    Runnable onDone;
    final boolean closeOnDone;
    final boolean showConsole;
    final String[] input;
    private final List<ProcessListener> myListeners = Lists.newLockFreeCopyOnWriteList();

    private ProcessHandler myHandler;

    public MyProcessInConsole(final Module module,
                              final GeneralCommandLine commandLine,
                              final Runnable onDone,
                              final boolean showConsole,
                              final boolean closeOnDone,
                              final String[] input) {
      this.module = module;
      this.commandLine = commandLine;
      this.onDone = onDone;
      this.closeOnDone = closeOnDone;
      this.input = input;
      this.showConsole = showConsole;
    }

    public ConsoleProcessDescriptor addProcessListener(@Nonnull ProcessListener listener) {
      if (myHandler != null) {
        myHandler.addProcessListener(listener);
      }
      else {
        myListeners.add(listener);
      }
      return this;
    }

    public ConsoleProcessDescriptor waitWith(ProgressIndicator progressIndicator) {
      if (myHandler != null) {
        doWait(progressIndicator);
      }
      return this;
    }

    private void doWait(ProgressIndicator progressIndicator) {
      while (!myHandler.waitFor(500)) {
        if (progressIndicator.isCanceled()) {
          myHandler.destroyProcess();
          break;
        }
      }
    }

    public void setHandler(ProcessHandler handler) {
      myHandler = handler;
      for (final ProcessListener listener : myListeners) {
        handler.addProcessListener(listener);
      }
    }
  }

  public static ConsoleProcessDescriptor executeProcess(final Module module,
                                                        final GeneralCommandLine commandLine,
                                                        final @Nullable Runnable onDone,
                                                        final boolean closeOnDone,
                                                        final String... input) {
    return getInstance(module.getProject()).executeProcess(module, commandLine, onDone, true, closeOnDone, input);
  }

  public ConsoleProcessDescriptor executeProcess(final Module module,
                                                 final GeneralCommandLine commandLine,
                                                 final @Nullable Runnable onDone,
                                                 boolean showConsole,
                                                 final boolean closeOnDone,
                                                 final String... input) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert module.getProject() == myProject;

    final MyProcessInConsole process = new MyProcessInConsole(module, commandLine, onDone, showConsole, closeOnDone, input);
    if (isExecuting()) {
      myProcessQueue.add(process);
    }
    else {
      executeProcessImpl(process, true);
    }
    return process;
  }

  public boolean isExecuting() {
    return myExecuting;
  }

  private void executeProcessImpl(final MyProcessInConsole pic, boolean toFocus) {
    final Module module = pic.module;
    final GeneralCommandLine commandLine = pic.commandLine;
    final String[] input = pic.input;
    final boolean closeOnDone = pic.closeOnDone;
    final Runnable onDone = pic.onDone;

    assert module.getProject() == myProject;

    myExecuting = true;

    // Module creation was cancelled
    if (module.isDisposed()) {
      return;
    }

    Application application = Application.get();
    final ModalityState modalityState = application.getCurrentModalityState();
    final boolean modalContext = modalityState != application.getNoneModalityState();

    if (!modalContext && pic.showConsole) {
      show(null, toFocus);
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    myConsole.print(commandLine.getCommandLineString(), ConsoleViewContentType.SYSTEM_OUTPUT);
    final ProcessHandler handler;
    try {
      handler = ProcessHandlerFactory.getInstance().createProcessHandler(commandLine);

      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      OutputStreamWriter writer = new OutputStreamWriter(handler.getProcessInput());
      for (String s : input) {
        writer.write(s);
      }
      writer.flush();

      final Ref<Boolean> gotError = new Ref<Boolean>(false);
      handler.addProcessListener(new ProcessAdapter() {
        public void onTextAvailable(ProcessEvent event, Key key) {
          if (key == ProcessOutputTypes.STDERR) {
            gotError.set(true);
          }
          LOG.debug("got text: " + event.getText());
        }

        public void processTerminated(ProcessEvent event) {
          final int exitCode = event.getExitCode();
          if (exitCode == 0 && !gotError.get().booleanValue()) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (myProject.isDisposed() || !closeOnDone) {
                  return;
                }
                myToolWindow.hide(null);
              }
            }, modalityState);
          }
        }
      });
    }
    catch (final Exception e) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(e.getMessage(), "Cannot Start Process");

          try {
            if (onDone != null && !module.isDisposed()) {
              onDone.run();
            }
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      }, modalityState);
      return;
    }

    pic.setHandler(handler);
    myKillAction.setHandler(handler);

    final MvcFramework framework = MvcFramework.getInstance(module);
    myToolWindow.setIcon(framework == null ? JetgroovyIcons.Groovy.Groovy_13x13 : framework.getToolWindowIcon());

    myContent.setDisplayName((framework == null ? "" : framework.getDisplayName() + ":") + "Executing...");
    myConsole.scrollToEnd();
    myConsole.attachToProcess(handler);
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        handler.startNotify();
        handler.waitFor();

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isDisposed()) {
              return;
            }

            module.putUserData(UPDATING_BY_CONSOLE_PROCESS, true);
            LocalFileSystem.getInstance().refresh(false);
            module.putUserData(UPDATING_BY_CONSOLE_PROCESS, null);

            try {
              if (onDone != null && !module.isDisposed()) {
                onDone.run();
              }
            }
            catch (Exception e) {
              LOG.error(e);
            }
            myConsole.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
            myKillAction.setHandler(null);
            myContent.setDisplayName("");

            myExecuting = false;

            final MyProcessInConsole pic = myProcessQueue.poll();
            if (pic != null) {
              executeProcessImpl(pic, false);
            }
          }
        }, modalityState);
      }
    });
  }

  public void dispose() {
  }

  private class MyKillProcessAction extends AnAction {
    private ProcessHandler myHandler = null;

    public MyKillProcessAction() {
      super("Kill process", "Kill process", ExecutionIconGroup.actionKillprocess());
    }

    public void setHandler(@Nullable ProcessHandler handler) {
      myHandler = handler;
    }

    @Override
    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(isEnabled());
    }

    public void actionPerformed(final AnActionEvent e) {
      if (myHandler != null) {
        myHandler.destroyProcess();
        myConsole.print("Process terminated", ConsoleViewContentType.ERROR_OUTPUT);
      }
    }

    public boolean isEnabled() {
      return myHandler != null;
    }
  }

  public ConsoleViewImpl getConsole() {
    return myConsole;
  }
}
