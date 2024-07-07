package consulo.groovy.impl.highlight;

import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.EditorHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.highlighter.GroovyEditorHighlighter;

/**
 * @author VISTALL
 * @since 2018-04-30
 */
@ExtensionImpl
public class GroovyEditorHighlighterProvider implements EditorHighlighterProvider {
  @Override
  public EditorHighlighter getEditorHighlighter(@Nullable Project project,
                                                @Nonnull FileType fileType,
                                                @Nullable VirtualFile virtualFile,
                                                @Nonnull EditorColorsScheme colors) {
    return new GroovyEditorHighlighter(colors);
  }

  @Nonnull
  @Override
  public FileType getFileType() {
    return GroovyFileType.INSTANCE;
  }
}
