package org.jetbrains.plugins.groovy.module.extension;

import javax.swing.JComponent;

import org.consulo.module.extension.MutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyMutableModuleExtension extends GroovyModuleExtension implements MutableModuleExtension<GroovyModuleExtension>
{
	public GroovyMutableModuleExtension(@NotNull String id, @NotNull ModifiableRootModel module)
	{
		super(id, module);
	}

	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nullable Runnable updateOnCheck)
	{
		return null;
	}

	@Override
	public void setEnabled(boolean val)
	{
		myIsEnabled = val;
	}

	@Override
	public boolean isModified(@NotNull GroovyModuleExtension extension)
	{
		return myIsEnabled != extension.isEnabled();
	}
}
