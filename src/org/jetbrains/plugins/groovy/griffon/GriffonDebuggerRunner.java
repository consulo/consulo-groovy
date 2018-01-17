package org.jetbrains.plugins.groovy.griffon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaCommandLine;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import consulo.java.execution.configurations.OwnJavaParameters;

/**
 * @author peter
 */
public class GriffonDebuggerRunner extends GenericDebuggerRunner
{
	@Override
	public boolean canRun(@NotNull final String executorId, @NotNull final RunProfile profile)
	{
		return executorId.equals(DefaultDebugExecutor.EXECUTOR_ID) && profile instanceof GriffonRunConfiguration;
	}

	@Override
	@NotNull
	public String getRunnerId()
	{
		return "GriffonDebugger";
	}

	@Nullable
	@Override
	protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws
			ExecutionException
	{
		final JavaCommandLine javaCommandLine = (JavaCommandLine) state;
		final OwnJavaParameters params = javaCommandLine.getJavaParameters();

		if(!params.getVMParametersList().hasProperty("griffon.full.stacktrace"))
		{
			params.getVMParametersList().add("-Dgriffon.full.stacktrace=true");
		}

		String address = null;
		try
		{
			for(String s : params.getProgramParametersList().getList())
			{
				if(s.startsWith("run-"))
				{
					// Application will be run in forked VM
					address = DebuggerUtils.getInstance().findAvailableDebugAddress(DebuggerSettings.SOCKET_TRANSPORT).address();
					params.getProgramParametersList().replaceOrAppend(s, s + " --debug --debugPort=" + address);
					break;
				}
			}
		}
		catch(ExecutionException ignored)
		{
		}

		if(address == null)
		{
			return super.createContentDescriptor(state, environment);
		}

		RemoteConnection connection = new RemoteConnection(true, "127.0.0.1", address, false);
		return attachVirtualMachine(state, environment, connection, true);
	}

}
