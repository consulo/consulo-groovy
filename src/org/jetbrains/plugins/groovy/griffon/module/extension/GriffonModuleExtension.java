package org.jetbrains.plugins.groovy.griffon.module.extension;

import org.consulo.module.extension.impl.ModuleExtensionImpl;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 14:33/30.06.13
 */
public class GriffonModuleExtension extends ModuleExtensionImpl<GriffonModuleExtension>
{
	public GriffonModuleExtension(@NotNull String id, @NotNull ModuleRootLayer module)
	{
		super(id, module);
	}
}
