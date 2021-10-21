package consulo.groovy.gant;

import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.gant.GantScriptType;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22/10/2021
 */
public class GantFileFileFactory extends FileTypeFactory
{
	@Override
	public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer)
	{
		fileTypeConsumer.consume(GroovyFileType.GROOVY_FILE_TYPE, GantScriptType.DEFAULT_EXTENSION);
	}
}
