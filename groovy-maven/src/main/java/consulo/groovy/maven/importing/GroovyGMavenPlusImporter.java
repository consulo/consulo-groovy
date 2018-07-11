package consulo.groovy.maven.importing;

import org.jetbrains.idea.maven.importing.GroovyImporter;

/**
 * @author VISTALL
 * @since 2018-07-11
 */
public class GroovyGMavenPlusImporter extends GroovyImporter {
    public GroovyGMavenPlusImporter() {
        super("org.codehaus.gmavenplus", "gmavenplus-plugin");
    }
}
