package org.jetbrains.plugins.groovy;

import com.intellij.debugger.engine.JVMDebugProvider;
import com.intellij.psi.PsiFile;

/**
 * @author VISTALL
 * @since 15.04.14
 */
public class GroovyJvmDebugProvider implements JVMDebugProvider
{
	@Override
	public boolean supportsJVMDebugging(PsiFile psiFile)
	{
		return psiFile.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
	}
}
