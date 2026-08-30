/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.impl.compiler.generator;

import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressIndicator;
import consulo.compiler.CompileContext;
import consulo.compiler.CompileContextEx;
import consulo.compiler.CompilerPaths;
import consulo.compiler.scope.CompileScope;
import consulo.compiler.scope.FileSetCompileScope;
import consulo.compiler.setting.ExcludedEntriesConfiguration;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ContentFolder;
import consulo.project.Project;
import consulo.util.collection.Chunk;
import consulo.util.collection.FactoryMap;
import consulo.util.io.FileUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.impl.compiler.GroovyCompilerBase;
import org.jetbrains.plugins.groovy.impl.compiler.GroovyCompilerConfiguration;
import org.jetbrains.plugins.groovy.impl.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * @author peter
 */
@ExtensionImpl(id = "groovy-stub-generator")
public class GroovycStubGenerator extends GroovyCompilerBase {
    private static Logger LOG = Logger.getInstance(GroovycStubGenerator.class);

    public static final String GROOVY_STUBS = "groovyStubs";

    @Inject
    public GroovycStubGenerator(Project project) {
        super(project);
    }

    @Override
    public void compile(CompileContext compileContext, Chunk<Module> moduleChunk, Collection<Path> files, OutputSink sink) {
        ExcludedEntriesConfiguration excluded = GroovyCompilerConfiguration.getExcludeConfiguration(myProject);

        Map<Pair<Module, Boolean>, Boolean> hasJava = FactoryMap.create(key -> containsJavaSources(key.first, key.second));

        CompileContextEx contextEx = (CompileContextEx)compileContext;

        List<Path> total = new ArrayList<>();
        for (Path file : files) {
            if (!excluded.isExcluded(file)
                && GroovyNamesUtil.isIdentifier(FileUtil.getNameWithoutExtension(file.getFileName().toString()))) {
                Module module = compileContext.getModuleByFile(file);
                if (module == null || hasJava.get(Pair.create(module, contextEx.isInTestSourceContent(file)))) {
                    total.add(file);
                }
            }
        }

        if (total.isEmpty()) {
            return;
        }

        super.compile(compileContext, moduleChunk, total, sink);
    }

    @Nonnull
    @Override
    public FileType[] getInputFileTypes() {
        return new FileType[]{
            JavaFileType.INSTANCE,
            GroovyFileType.INSTANCE
        };
    }

    @Nonnull
    @Override
    public FileType[] getOutputFileTypes() {
        return new FileType[]{JavaFileType.INSTANCE};
    }

    private static boolean containsJavaSources(Module module, boolean inTests) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        for (ContentEntry entry : rootManager.getContentEntries()) {
            for (ContentFolder folder : entry.getFolders(LanguageContentFolderScopes.all(false))) {
                VirtualFile dir = folder.getFile();
                if ((!inTests && folder.getType() == ProductionContentFolderTypeProvider.getInstance() || folder.getType() ==
                    TestContentFolderTypeProvider.getInstance() && inTests) && dir != null) {
                    if (!rootManager.getFileIndex().iterateContentUnderDirectory(
                        dir,
                        fileOrDir -> fileOrDir.isDirectory() || JavaFileType.INSTANCE != fileOrDir.getFileType()
                    )) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected void compileFiles(CompileContext compileContext,
                                Module module,
                                List<Path> toCompile,
                                OutputSink sink,
                                boolean tests) {
        File outDir = getStubOutput(module, tests);
        outDir.mkdirs();

        Path outputRoot = outDir.toPath();
        cleanDirectory(outputRoot);

        ((CompileContextEx)compileContext).assignModule(outputRoot, module, tests, this);

        ProgressIndicator indicator = compileContext.getProgressIndicator();
        indicator.pushState();

        try {
            LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            List<VirtualFile> virtualFiles = new ArrayList<>();
            for (Path path : toCompile) {
                VirtualFile file = localFileSystem.findFileByNioFile(path);
                if (file != null) {
                    virtualFiles.add(file);
                }
            }

            GroovyToJavaGenerator generator = new GroovyToJavaGenerator(myProject, new HashSet<>(virtualFiles));
            for (int i = 0; i < virtualFiles.size(); i++) {
                indicator.setFraction((double)i / virtualFiles.size());

                Collection<Path> stubFiles = generateItems(generator, virtualFiles.get(i), outputRoot, compileContext, myProject);
                ((CompileContextEx)compileContext).addScope(new FileSetCompileScope(stubFiles, new Module[]{module}));
            }
        }
        finally {
            indicator.popState();
        }
    }

    private static File getStubOutput(Module module, boolean tests) {
        Project project = module.getProject();
        String rootPath = CompilerPaths.getGeneratedDataDirectory(project).getPath() + "/" + GROOVY_STUBS + "/";
        return new File(rootPath + module.getName() + "/" + (tests ? "tests" : "production") + "/");
    }

    @Nullable
    @RequiredReadAction
    public static PsiClass findClassByStub(Project project, VirtualFile stubFile) {
        String[] components = StringUtil.trimEnd(stubFile.getPath(), ".java").split("[\\\\/]");
        int stubs = Arrays.asList(components).indexOf(GROOVY_STUBS);
        if (stubs < 0 || stubs >= components.length - 3) {
            return null;
        }

        String moduleName = components[stubs + 1];
        Module module = ModuleManager.getInstance(project).findModuleByName(moduleName);
        if (module == null) {
            return null;
        }

        String fqn = StringUtil.join(Arrays.asList(components).subList(stubs + 3, components.length), ".");
        return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.moduleScope(module));
    }

    private static void cleanDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    }
                    catch (IOException e) {
                        LOG.info(e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e) {
            LOG.info(e);
        }
    }

    @Nonnull
    @Override
    public String getDescription() {
        return "Groovy to java source code generator";
    }

    @Override
    public boolean validateConfiguration(CompileScope scope) {
        return true;
    }

    public static Collection<Path> generateItems(GroovyToJavaGenerator generator,
                                                 VirtualFile item,
                                                 Path outputRootDirectory,
                                                 CompileContext context,
                                                 Project project) {
        ProgressIndicator indicator = context.getProgressIndicator();
        indicator.setText("Generating stubs for " + item.getName() + "...");

        Map<String, CharSequence> output =
            ReadAction.compute(() -> generator.generateStubs((GroovyFile)PsiManager.getInstance(project).findFile(item)));

        return writeStubs(outputRootDirectory, output, item);
    }

    private static List<Path> writeStubs(Path outputRootDirectory, Map<String, CharSequence> output, VirtualFile src) {
        List<Path> stubs = new ArrayList<>();
        for (String relativePath : output.keySet()) {
            File stubFile = new File(outputRootDirectory.toFile(), relativePath);
            FileUtil.createIfDoesntExist(stubFile);
            try {
                FileUtil.writeToFile(stubFile, output.get(relativePath).toString().getBytes(src.getCharset()));
            }
            catch (IOException e) {
                LOG.error(e);
            }
            stubs.add(stubFile.toPath());
        }
        return stubs;
    }
}
