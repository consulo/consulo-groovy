package consulo.groovy.module.extension;

import javax.annotation.Nonnull;

import consulo.module.extension.ModuleExtension;
import consulo.module.extension.impl.ModuleExtensionImpl;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 14:47/28.05.13
 */
public class GroovyModuleExtension extends ModuleExtensionImpl<GroovyModuleExtension> implements ModuleExtension<GroovyModuleExtension>
{
	public GroovyModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}
}
