package org.jetbrains.idea.maven.groovy.importing;

import consulo.content.ContentFolderTypeProvider;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.maven.importing.MavenImporterFromBuildPlugin;
import consulo.module.Module;
import org.jdom.Element;
import org.jetbrains.idea.maven.importing.MavenContentFolder;
import org.jetbrains.idea.maven.importing.MavenModifiableModelsProvider;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public abstract class GroovyImporter extends MavenImporterFromBuildPlugin {
    public GroovyImporter(String pluginGroupID, String pluginArtifactID) {
        super(pluginGroupID, pluginArtifactID);
    }

    @Override
    public void preProcess(Module module,
                           MavenProject mavenProject,
                           MavenProjectChanges changes,
                           MavenModifiableModelsProvider modifiableModelsProvider) {
    }

    @Override
    public void process(MavenModifiableModelsProvider modifiableModelsProvider, Module module, MavenRootModelAdapter rootModel,
                        MavenProjectsTree mavenModel,
                        MavenProject mavenProject,
                        MavenProjectChanges changes,
                        Map<MavenProject, String> mavenProjectToModuleName,
                        List<MavenProjectsProcessorTask> postTasks) {

        enableModuleExtension(module, modifiableModelsProvider, GroovyModuleExtension.class);
    }

    @Override
    public void collectContentFolders(MavenProject mavenProject,
                                      BiFunction<ContentFolderTypeProvider, String, MavenContentFolder> folderAcceptor) {
        collectSourceOrTestFolders(mavenProject, "compile", "src/main/groovy", path -> {
            folderAcceptor.apply(ProductionContentFolderTypeProvider.getInstance(), path);
        });

        collectSourceOrTestFolders(mavenProject, "testCompile", "src/test/groovy", path -> {
            folderAcceptor.apply(TestContentFolderTypeProvider.getInstance(), path);
        });
    }

    private void collectSourceOrTestFolders(MavenProject mavenProject, String goal, String defaultDir, Consumer<String> consumer) {
        Element sourcesElement = getGoalConfig(mavenProject, goal);
        List<String> dirs = MavenJDOMUtil.findChildrenValuesByPath(sourcesElement, "sources", "fileset.directory");
        if (dirs.isEmpty()) {
            consumer.accept(mavenProject.getDirectory() + "/" + defaultDir);
            return;
        }

        dirs.forEach(consumer);
    }

    @Override
    public void collectExcludedFolders(MavenProject mavenProject, Consumer<String> result) {
        String stubsDir = findGoalConfigValue(mavenProject, "generateStubs", "outputDirectory");
        String testStubsDir = findGoalConfigValue(mavenProject, "generateTestStubs", "outputDirectory");

        // exclude common parent of /groovy-stubs/main and /groovy-stubs/test
        String defaultStubsDir = mavenProject.getGeneratedSourcesDirectory(false) + "/groovy-stubs";

        result.accept(stubsDir == null ? defaultStubsDir : stubsDir);
        result.accept(testStubsDir == null ? defaultStubsDir : testStubsDir);
    }
}
