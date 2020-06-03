package consulo.groovy.module.extension;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import consulo.disposer.Disposable;
import consulo.module.extension.MutableModuleExtension;
import consulo.roots.ModuleRootLayer;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyMutableModuleExtension extends GroovyModuleExtension implements MutableModuleExtension<GroovyModuleExtension>
{
	public GroovyMutableModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public Component createConfigurationComponent(@Nonnull Disposable disposable, @Nonnull Runnable runnable)
	{
		return null;
	}

	@Override
	public void setEnabled(boolean val)
	{
		myIsEnabled = val;
	}

	@Override
	public boolean isModified(@Nonnull GroovyModuleExtension extension)
	{
		return myIsEnabled != extension.isEnabled();
	}
}
