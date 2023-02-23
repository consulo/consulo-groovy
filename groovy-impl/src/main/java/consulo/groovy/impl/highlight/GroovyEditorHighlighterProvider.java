package consulo.groovy.impl.highlight;

import consulo.codeEditor.EditorHighlighter;
import consulo.colorScheme.EditorColorsScheme;
import consulo.language.editor.highlight.EditorHighlighterProvider;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.highlighter.GroovyEditorHighlighter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2018-04-30
 */
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
    return GroovyFileType.GROOVY_FILE_TYPE;
  }
}
