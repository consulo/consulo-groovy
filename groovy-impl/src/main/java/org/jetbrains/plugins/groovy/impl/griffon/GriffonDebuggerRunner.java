package org.jetbrains.plugins.groovy.impl.griffon;

import com.intellij.java.debugger.engine.DebuggerUtils;
import com.intellij.java.debugger.impl.GenericDebuggerRunner;
import com.intellij.java.debugger.impl.settings.DebuggerSettings;
import com.intellij.java.execution.configurations.JavaCommandLine;
import com.intellij.java.execution.configurations.RemoteConnection;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.RunProfile;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.ui.RunContentDescriptor;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.process.ExecutionException;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author peter
 */
@ExtensionImpl
public class GriffonDebuggerRunner extends GenericDebuggerRunner {
  @Override
  public boolean canRun(@Nonnull final String executorId, @Nonnull final RunProfile profile) {
    return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof GriffonRunConfiguration;
  }

  @Override
  @Nonnull
  public String getRunnerId() {
    return "GriffonDebugger";
  }

  @Nullable
  @Override
  protected RunContentDescriptor createContentDescriptor(@Nonnull RunProfileState state, @Nonnull ExecutionEnvironment environment) throws
    ExecutionException {
    final JavaCommandLine javaCommandLine = (JavaCommandLine)state;
    final OwnJavaParameters params = javaCommandLine.getJavaParameters();

    if (!params.getVMParametersList().hasProperty("griffon.full.stacktrace")) {
      params.getVMParametersList().add("-Dgriffon.full.stacktrace=true");
    }

    String address = null;
    try {
      for (String s : params.getProgramParametersList().getList()) {
        if (s.startsWith("run-")) {
          // Application will be run in forked VM
          address = DebuggerUtils.getInstance().findAvailableDebugAddress(DebuggerSettings.SOCKET_TRANSPORT).address();
          params.getProgramParametersList().replaceOrAppend(s, s + " --debug --debugPort=" + address);
          break;
        }
      }
    }
    catch (ExecutionException ignored) {
    }

    if (address == null) {
      return super.createContentDescriptor(state, environment);
    }

    RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", address, false);
    return attachVirtualMachine(state, environment, connection, true);
  }

}
