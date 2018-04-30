package consulo.groovy.module.extension;

import javax.annotation.Nonnull;

import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.roots.ModuleRootLayer;

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
