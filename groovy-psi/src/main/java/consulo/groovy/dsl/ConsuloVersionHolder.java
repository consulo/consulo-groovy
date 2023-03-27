package consulo.groovy.dsl;

import consulo.application.Application;

/**
 * @author VISTALL
 * @since 03/03/2023
 */
public class ConsuloVersionHolder {
  public static final String ideaVersion;

  static {
    int major = Application.get().getVersion().getMajor();
    int minor = Application.get().getVersion().getMinor();
    ideaVersion = major + "." + minor;
  }
}
