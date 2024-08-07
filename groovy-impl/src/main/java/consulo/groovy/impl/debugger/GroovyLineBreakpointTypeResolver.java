package consulo.groovy.impl.debugger;

import consulo.annotation.component.ExtensionImpl;
import consulo.java.debugger.impl.BaseJavaLineBreakpointTypeResolver;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author VISTALL
 * @since 25/02/2023
 */
@ExtensionImpl
public class GroovyLineBreakpointTypeResolver extends BaseJavaLineBreakpointTypeResolver {
  @Nonnull
  @Override
  public FileType getFileType() {
    return GroovyFileType.INSTANCE;
  }
}
