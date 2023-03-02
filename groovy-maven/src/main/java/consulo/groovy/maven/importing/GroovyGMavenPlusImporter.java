package consulo.groovy.maven.importing;

import consulo.annotation.component.ExtensionImpl;
import org.jetbrains.idea.maven.groovy.importing.GroovyImporter;

/**
 * @author VISTALL
 * @since 2018-07-11
 */
@ExtensionImpl
public class GroovyGMavenPlusImporter extends GroovyImporter {
  public GroovyGMavenPlusImporter() {
    super("org.codehaus.gmavenplus", "gmavenplus-plugin");
  }
}
