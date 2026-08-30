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

package org.jetbrains.plugins.groovy.impl.compiler;

import com.intellij.java.compiler.impl.cache.JavaDependencyCache;
import com.intellij.java.language.impl.JavaFileType;
import com.intellij.java.language.projectRoots.JavaSdkType;
import com.intellij.java.language.util.cls.ClsFormatException;
import consulo.application.AccessRule;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.compiler.*;
import consulo.compiler.resourceCompiler.ResourceCompilerConfiguration;
import consulo.compiler.scope.FileSetCompileScope;
import consulo.compiler.util.CompilerUtil;
import consulo.container.boot.ContainerPathManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.library.Library;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.http.HttpProxyManager;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.execution.projectRoots.OwnJdkUtil;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.collection.Chunk;
import consulo.util.collection.SmartList;
import consulo.util.io.CharsetToolkit;
import consulo.util.io.ClassPathUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.virtualFileSystem.util.PathsList;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerMessageCategories;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.impl.runner.GroovyScriptUtil;
import org.jetbrains.plugins.groovy.impl.runner.GroovycOSProcessHandler;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author peter
 */
public abstract class GroovyCompilerBase implements TranslatingCompiler {
    private static final Logger LOG = Logger.getInstance(GroovyCompilerBase.class);
    protected final Project myProject;

    public GroovyCompilerBase(Project project) {
        myProject = project;
    }

    protected void runGroovycCompiler(CompileContext compileContext,
                                      Module module,
                                      List<Path> toCompile,
                                      boolean forStubs,
                                      Path outputDir,
                                      OutputSink sink,
                                      boolean tests) {
        Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
        assert sdk != null; //verified before

        OwnJavaParameters parameters = new OwnJavaParameters();
        PathsList classPathBuilder = parameters.getClassPath();

        // IMPORTANT: must be the first entry to avoid collisions
        classPathBuilder.add(ClassPathUtil.getJarPathForClass(GroovycRunner.class));

        ModuleChunk chunk = createChunk(module, compileContext);

        Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module);
        if (libraries.length > 0) {
            classPathBuilder.addVirtualFiles(Arrays.asList(libraries[0].getFiles(BinariesOrderRootType.ID)));
        }

        JavaSdkType javaSdkType = (JavaSdkType)sdk.getSdkType();
        for (Path path : chunk.getCompilationBootClasspathFiles(javaSdkType, false)) {
            classPathBuilder.add(path.toFile());
        }
        for (Path path : chunk.getCompilationClasspathFiles(javaSdkType, false)) {
            classPathBuilder.add(path.toFile());
        }
        appendOutputPath(module, classPathBuilder, false);
        if (tests) {
            appendOutputPath(module, classPathBuilder, true);
        }

        List<String> patchers = new SmartList<>();

        AccessRule.read(() -> {
            for (GroovyCompilerExtension extension : GroovyCompilerExtension.EP_NAME.getExtensions()) {
                extension.enhanceCompilationClassPath(chunk, classPathBuilder);
                patchers.addAll(extension.getCompilationUnitPatchers(chunk));
            }
        });

        boolean profileGroovyc = "true".equals(System.getProperty("profile.groovy.compiler"));
        if (profileGroovyc) {
            parameters.getVMParametersList().defineProperty("java.library.path", ContainerPathManager.get().getBinPath());
            parameters.getVMParametersList().defineProperty("profile.groovy.compiler", "true");
            parameters.getVMParametersList().add("-agentlib:yjpagent=disablej2ee,disablealloc,sessionname=GroovyCompiler");
            classPathBuilder.add(ContainerPathManager.get().findFileInLibDirectory("yjp-controller-api-redist.jar").getAbsolutePath());
        }

        GroovyCompilerConfiguration compilerConfiguration = GroovyCompilerConfiguration.getInstance(myProject);
        parameters.getVMParametersList().add("-Xmx" + compilerConfiguration.getHeapSize() + "m");
        if (profileGroovyc) {
            parameters.getVMParametersList().add("-XX:+HeapDumpOnOutOfMemoryError");
        }
        List<Pair<String, String>> jvmProperties = HttpProxyManager.getInstance().getJvmProperties(false, null);
        for (Pair<String, String> jvmProperty : jvmProperties) {
            parameters.getVMParametersList().add(jvmProperty.getKey(), jvmProperty.getValue());
        }

        // Setting up process encoding according to locale
        List<String> list = new ArrayList<>();
        CompilerUtil.addLocaleOptions(list, false);
        for (String s : list) {
            parameters.getVMParametersList().add(s);
        }

        parameters.setMainClass(GroovycRunner.class.getName());

        Path finalOutputDir = getMainOutput(compileContext, module, tests);
        if (finalOutputDir == null) {
            compileContext.newError(LocalizeValue.localizeTODO(
                "No output directory for module " + module.getName() + (tests ? " tests" : " production"))).add();
            return;
        }

        Charset ideCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
        String encoding =
            ideCharset != null && !Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset) ? ideCharset.name() : null;
        Set<String> paths2Compile = new HashSet<>();
        for (Path path : toCompile) {
            paths2Compile.add(FileUtil.toSystemIndependentName(path.toString()));
        }
        Map<String, String> class2Src = new HashMap<>();

        TranslatingCompilerFilesMonitor monitor = TranslatingCompilerFilesMonitor.getInstance();
        for (VirtualFile file : enumerateGroovyFiles(module)) {
            if (!paths2Compile.contains(file.getPath())) {
                for (String name : monitor.getCompiledClassNames(file.toNioPath(), myProject)) {
                    class2Src.put(name, file.getPath());
                }
            }
        }

        File fileWithParameters;
        try {
            fileWithParameters = GroovycOSProcessHandler.fillFileWithGroovycParameters(
                outputDir.toString(),
                paths2Compile,
                FileUtil.toSystemDependentName(finalOutputDir.toString()),
                class2Src,
                encoding,
                patchers);
        }
        catch (IOException e) {
            LOG.info(e);
            compileContext.newError(LocalizeValue.localizeTODO(
                "Error creating a temp file to launch Groovy compiler: " + e.getMessage())).add();
            return;
        }

        parameters.getProgramParametersList().add(forStubs ? "stubs" : "groovyc");
        parameters.getProgramParametersList().add(fileWithParameters.getPath());
        if (compilerConfiguration.isInvokeDynamic()) {
            parameters.getProgramParametersList().add("--indy");
        }

        try {
            parameters.setJdk(sdk);

            GeneralCommandLine generalCommandLine = OwnJdkUtil.setupJVMCommandLine(parameters);
            GroovycOSProcessHandler processHandler =
                GroovycOSProcessHandler.runGroovyc(generalCommandLine, s -> compileContext.getProgressIndicator().setText(s));

            List<Path> toRecompile = new ArrayList<>();
            for (File toRecompileFile : processHandler.getToRecompileFiles()) {
                toRecompile.add(toRecompileFile.toPath());
            }

            for (CompilerMessage compilerMessage : processHandler.getCompilerMessages(module.getName())) {
                String url = compilerMessage.getUrl();
                compileContext.newMessage(getMessageCategory(compilerMessage), LocalizeValue.of(compilerMessage.getMessage()))
                    .optionalUrl(url == null ? null : VirtualFileUtil.pathToUrl(FileUtil.toSystemIndependentName(url)))
                    .position((int)compilerMessage.getLineNum(), (int)compilerMessage.getColumnNum())
                    .add();
            }

            List<GroovycOSProcessHandler.OutputItem> outputItems = processHandler.getSuccessfullyCompiled();
            List<OutputItem> items = new ArrayList<>();
            if (forStubs) {
                List<String> outputPaths = new ArrayList<>();
                for (GroovycOSProcessHandler.OutputItem outputItem : outputItems) {
                    outputPaths.add(outputItem.outputPath);
                }
                addStubsToCompileScope(outputPaths, compileContext, module);
            }
            else {
                ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                if (indicator != null) {
                    indicator.setText("Updating caches...");
                }

                JavaDependencyCache dependencyCache =
                    ((CompileContextEx)compileContext).getDependencyCache().findChild(JavaDependencyCache.class);
                for (GroovycOSProcessHandler.OutputItem outputItem : outputItems) {
                    Path sourceFile = Path.of(outputItem.sourcePath);
                    if (!Files.exists(sourceFile)) {
                        continue;
                    }

                    if (indicator != null) {
                        indicator.setText2(sourceFile.getFileName().toString());
                    }

                    items.add(new OutputItem(FileUtil.toSystemIndependentName(outputItem.outputPath), sourceFile));

                    File classFile = new File(outputItem.outputPath);
                    try {
                        dependencyCache.reparseClassFile(classFile, Files.readAllBytes(classFile.toPath()));
                    }
                    catch (ClsFormatException | CacheCorruptedException e) {
                        LOG.error(e);
                    }
                    catch (FileNotFoundException ignored) {
                    }
                    catch (IOException e) {
                        LOG.error(e);
                    }
                }
            }

            sink.add(FileUtil.toSystemIndependentName(outputDir.toString()), items, toRecompile);
        }
        catch (ExecutionException e) {
            LOG.info(e);
            compileContext.newError(LocalizeValue.localizeTODO("Error running Groovy compiler: " + e.getMessage())).add();
        }
    }

    protected Set<VirtualFile> enumerateGroovyFiles(Module module) {
        Set<VirtualFile> moduleClasses = new HashSet<>();
        ModuleRootManager.getInstance(module).getFileIndex().iterateContent(vfile -> {
            if (!vfile.isDirectory() && GroovyFileType.GROOVY_FILE_TYPE.equals(vfile.getFileType())) {
                AccessRule.read(() -> {
                    if (PsiManager.getInstance(myProject).findFile(vfile) instanceof GroovyFile) {
                        moduleClasses.add(vfile);
                    }
                });
            }
            return true;
        });
        return moduleClasses;
    }

    protected static void addStubsToCompileScope(List<String> outputPaths, CompileContext compileContext, Module module) {
        List<Path> stubFiles = new ArrayList<>();
        for (String outputPath : outputPaths) {
            stubFiles.add(Path.of(outputPath));
        }
        ((CompileContextEx)compileContext).addScope(new FileSetCompileScope(stubFiles, new Module[]{module}));
    }

    protected static @Nullable Path getMainOutput(CompileContext compileContext, Module module, boolean tests) {
        return tests ? compileContext.getModuleOutputDirectoryForTests(module) : compileContext.getModuleOutputDirectory(module);
    }

    private static CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
        String category = compilerMessage.getCategory();

        if (category.equals(GroovyCompilerMessageCategories.ERROR)) {
            return CompilerMessageCategory.ERROR;
        }
        if (category.equals(GroovyCompilerMessageCategories.INFORMATION)) {
            return CompilerMessageCategory.INFORMATION;
        }
        if (category.equals(GroovyCompilerMessageCategories.WARNING)) {
            return CompilerMessageCategory.WARNING;
        }
        if (category.equals(GroovyCompilerMessageCategories.STATISTICS)) {
            return CompilerMessageCategory.STATISTICS;
        }

        return CompilerMessageCategory.ERROR;
    }

    private static void appendOutputPath(Module module, PathsList compileClasspath, boolean forTestClasses) {
        String output = CompilerPaths.getModuleOutputPath(
            module,
            forTestClasses ? TestContentFolderTypeProvider.getInstance() : ProductionContentFolderTypeProvider.getInstance());
        if (output != null) {
            compileClasspath.add(FileUtil.toSystemDependentName(output));
        }
    }

    private static ModuleChunk createChunk(Module module, CompileContext context) {
        return new ModuleChunk((CompileContextEx)context, new Chunk<>(module), Collections.emptyMap());
    }

    @Override
    public void compile(CompileContext compileContext, Chunk<Module> moduleChunk, Collection<Path> files, OutputSink sink) {
        Map<Module, List<Path>> mapModulesToFiles;
        if (moduleChunk.getNodes().size() == 1) {
            mapModulesToFiles = Collections.singletonMap(moduleChunk.getNodes().iterator().next(), new ArrayList<>(files));
        }
        else {
            mapModulesToFiles = CompilerUtil.buildModuleToFilesMap(compileContext, files);
        }
        for (Module module : moduleChunk.getNodes()) {
            GroovyModuleExtension extension = ModuleUtilCore.getExtension(module, GroovyModuleExtension.class);
            if (extension == null) {
                continue;
            }

            List<Path> moduleFiles = mapModulesToFiles.get(module);
            if (moduleFiles == null) {
                continue;
            }

            CompileContextEx contextEx = (CompileContextEx)compileContext;
            List<Path> toCompile = new ArrayList<>();
            List<Path> toCompileTests = new ArrayList<>();
            PsiManager psiManager = PsiManager.getInstance(myProject);

            for (Path file : moduleFiles) {
                if (shouldCompile(file, psiManager)) {
                    (contextEx.isInTestSourceContent(file) ? toCompileTests : toCompile).add(file);
                }
            }

            if (!toCompile.isEmpty()) {
                compileFiles(compileContext, module, toCompile, sink, false);
            }
            if (!toCompileTests.isEmpty()) {
                compileFiles(compileContext, module, toCompileTests, sink, true);
            }
        }
    }

    private static boolean shouldCompile(Path file, PsiManager manager) {
        if (ResourceCompilerConfiguration.getInstance(manager.getProject()).isResourceFile(file)) {
            return false;
        }

        FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(file.getFileName().toString());
        if (fileType == GroovyFileType.GROOVY_FILE_TYPE) {
            return AccessRule.read(() -> {
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
                if (virtualFile == null) {
                    return true;
                }
                PsiFile psiFile = manager.findFile(virtualFile);
                if (psiFile instanceof GroovyFile && ((GroovyFile)psiFile).isScript()) {
                    GroovyScriptType scriptType = GroovyScriptUtil.getScriptType((GroovyFile)psiFile);
                    return scriptType.shouldBeCompiled((GroovyFile)psiFile);
                }
                return true;
            });
        }

        return fileType == JavaFileType.INSTANCE;
    }

    protected abstract void compileFiles(CompileContext compileContext,
                                         Module module,
                                         List<Path> toCompile,
                                         OutputSink sink,
                                         boolean tests);

    @Override
    public boolean isCompilableFile(Path file, CompileContext context) {
        return GroovyFileType.GROOVY_FILE_TYPE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(file.getFileName().toString()));
    }
}
