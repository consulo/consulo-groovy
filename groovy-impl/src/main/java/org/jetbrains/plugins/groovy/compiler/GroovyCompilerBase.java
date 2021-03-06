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

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.cache.JavaDependencyCache;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.FileSetCompileScope;
import com.intellij.compiler.impl.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.OutputItemImpl;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.*;
import com.intellij.util.cls.ClsFormatException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import consulo.application.AccessRule;
import consulo.compiler.impl.TranslatingCompilerFilesMonitor;
import consulo.compiler.impl.resourceCompiler.ResourceCompilerConfiguration;
import consulo.compiler.roots.CompilerPathsImpl;
import consulo.container.boot.ContainerPathManager;
import consulo.groovy.module.extension.GroovyModuleExtension;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.java.projectRoots.OwnJdkUtil;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerMessageCategories;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;
import org.jetbrains.plugins.groovy.runner.GroovycOSProcessHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author peter
 */
public abstract class GroovyCompilerBase implements TranslatingCompiler
{
	private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompilerBase");
	protected final Project myProject;

	public GroovyCompilerBase(Project project)
	{
		myProject = project;
	}

	protected void runGroovycCompiler(final CompileContext compileContext, final Module module, final List<VirtualFile> toCompile, boolean forStubs, VirtualFile outputDir, OutputSink sink, boolean tests)
	{
		//assert !ApplicationManager.getApplication().isDispatchThread();
		final Sdk sdk = ModuleUtilCore.getSdk(module, JavaModuleExtension.class);
		assert sdk != null; //verified before

		final OwnJavaParameters parameters = new OwnJavaParameters();
		final PathsList classPathBuilder = parameters.getClassPath();

		// IMPORTANT: must be the first entry to avoid collisions
		classPathBuilder.add(PathUtil.getJarPathForClass(GroovycRunner.class));

		final ModuleChunk chunk = createChunk(module, compileContext);

		final Library[] libraries = GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module);
		if(libraries.length > 0)
		{
			classPathBuilder.addVirtualFiles(Arrays.asList(libraries[0].getFiles(OrderRootType.CLASSES)));
		}

		classPathBuilder.addVirtualFiles(chunk.getCompilationBootClasspathFiles(JavaSdk.getInstance(), false));
		classPathBuilder.addVirtualFiles(chunk.getCompilationClasspathFiles(JavaSdk.getInstance(), false));
		appendOutputPath(module, classPathBuilder, false);
		if(tests)
		{
			appendOutputPath(module, classPathBuilder, true);
		}

		final List<String> patchers = new SmartList<String>();

		AccessRule.read(() ->
		{
			for(final GroovyCompilerExtension extension : GroovyCompilerExtension.EP_NAME.getExtensions())
			{
				extension.enhanceCompilationClassPath(chunk, classPathBuilder);
				patchers.addAll(extension.getCompilationUnitPatchers(chunk));
			}
		});

		final boolean profileGroovyc = "true".equals(System.getProperty("profile.groovy.compiler"));
		if(profileGroovyc)
		{
			parameters.getVMParametersList().defineProperty("java.library.path", ContainerPathManager.get().getBinPath());
			parameters.getVMParametersList().defineProperty("profile.groovy.compiler", "true");
			parameters.getVMParametersList().add("-agentlib:yjpagent=disablej2ee,disablealloc,sessionname=GroovyCompiler");
			classPathBuilder.add(ContainerPathManager.get().findFileInLibDirectory("yjp-controller-api-redist.jar").getAbsolutePath());
		}

		final GroovyCompilerConfiguration compilerConfiguration = GroovyCompilerConfiguration.getInstance(myProject);
		parameters.getVMParametersList().add("-Xmx" + compilerConfiguration.getHeapSize() + "m");
		if(profileGroovyc)
		{
			parameters.getVMParametersList().add("-XX:+HeapDumpOnOutOfMemoryError");
		}
		parameters.getVMParametersList().addAll(HttpConfigurable.convertArguments(HttpConfigurable.getJvmPropertiesList(false, null)));

		//debug
		//parameters.getVMParametersList().add("-Xdebug"); parameters.getVMParametersList().add("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

		// Setting up process encoding according to locale
		final ArrayList<String> list = new ArrayList<String>();
		CompilerUtil.addLocaleOptions(list, false);
		for(String s : list)
		{
			parameters.getVMParametersList().add(s);
		}

		parameters.setMainClass(GroovycRunner.class.getName());

		final VirtualFile finalOutputDir = getMainOutput(compileContext, module, tests);
		if(finalOutputDir == null)
		{
			compileContext.addMessage(CompilerMessageCategory.ERROR, "No output directory for module " + module.getName() + (tests ? " tests" : " production"), null, -1, -1);
			return;
		}

		final Charset ideCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
		String encoding = ideCharset != null && !Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset) ? ideCharset.name() : null;
		Set<String> paths2Compile = ContainerUtil.map2Set(toCompile, new Function<VirtualFile, String>()
		{
			@Override
			public String fun(VirtualFile file)
			{
				return file.getPath();
			}
		});
		Map<String, String> class2Src = new HashMap<String, String>();

		for(VirtualFile file : enumerateGroovyFiles(module))
		{
			if(!paths2Compile.contains(file.getPath()))
			{
				for(String name : TranslatingCompilerFilesMonitor.getInstance().getCompiledClassNames(file, myProject))
				{
					class2Src.put(name, file.getPath());
				}
			}
		}

		final File fileWithParameters;
		try
		{
			fileWithParameters = GroovycOSProcessHandler.fillFileWithGroovycParameters(outputDir.getPath(), paths2Compile, FileUtil.toSystemDependentName(finalOutputDir.getPath()), class2Src, encoding, patchers);
		}
		catch(IOException e)
		{
			LOG.info(e);
			compileContext.addMessage(CompilerMessageCategory.ERROR, "Error creating a temp file to launch Groovy compiler: " + e.getMessage(), null, -1, -1);
			return;
		}

		parameters.getProgramParametersList().add(forStubs ? "stubs" : "groovyc");
		parameters.getProgramParametersList().add(fileWithParameters.getPath());
		if(compilerConfiguration.isInvokeDynamic())
		{
			parameters.getProgramParametersList().add("--indy");
		}

		try
		{
			parameters.setJdk(sdk);

			GeneralCommandLine generalCommandLine = OwnJdkUtil.setupJVMCommandLine(parameters);
			GroovycOSProcessHandler processHandler = GroovycOSProcessHandler.runGroovyc(generalCommandLine, s -> compileContext.getProgressIndicator().setText(s));

			final List<VirtualFile> toRecompile = new ArrayList<VirtualFile>();
			for(File toRecompileFile : processHandler.getToRecompileFiles())
			{
				final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(toRecompileFile);
				LOG.assertTrue(vFile != null);
				toRecompile.add(vFile);
			}

			for(CompilerMessage compilerMessage : processHandler.getCompilerMessages(module.getName()))
			{
				final String url = compilerMessage.getUrl();
				compileContext.addMessage(getMessageCategory(compilerMessage), compilerMessage.getMessage(), url == null ? null : VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(url)), (int) compilerMessage.getLineNum(), (int) compilerMessage.getColumnNum());
			}

			List<GroovycOSProcessHandler.OutputItem> outputItems = processHandler.getSuccessfullyCompiled();
			ArrayList<OutputItem> items = new ArrayList<OutputItem>();
			if(forStubs)
			{
				List<String> outputPaths = new ArrayList<String>();
				for(final GroovycOSProcessHandler.OutputItem outputItem : outputItems)
				{
					outputPaths.add(outputItem.outputPath);
				}
				addStubsToCompileScope(outputPaths, compileContext, module);
			}
			else
			{
				final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
				if(indicator != null)
				{
					indicator.setText("Updating caches...");
				}

				final JavaDependencyCache dependencyCache = ((CompileContextEx) compileContext).getDependencyCache().findChild(JavaDependencyCache.class);
				for(GroovycOSProcessHandler.OutputItem outputItem : outputItems)
				{
					final VirtualFile sourceVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(outputItem.sourcePath));
					if(sourceVirtualFile == null)
					{
						continue;
					}

					if(indicator != null)
					{
						indicator.setText2(sourceVirtualFile.getName());
					}

					LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(outputItem.outputPath));
					items.add(new OutputItemImpl(outputItem.outputPath, sourceVirtualFile));

					final File classFile = new File(outputItem.outputPath);
					try
					{
						dependencyCache.reparseClassFile(classFile, FileUtil.loadFileBytes(classFile));
					}
					catch(ClsFormatException e)
					{
						LOG.error(e);
					}
					catch(CacheCorruptedException e)
					{
						LOG.error(e);
					}
					catch(FileNotFoundException ignored)
					{
					}
					catch(IOException e)
					{
						LOG.error(e);
					}
				}
			}

			sink.add(outputDir.getPath(), items, VfsUtil.toVirtualFileArray(toRecompile));
		}
		catch(ExecutionException e)
		{
			LOG.info(e);
			compileContext.addMessage(CompilerMessageCategory.ERROR, "Error running Groovy compiler: " + e.getMessage(), null, -1, -1);
		}
	}

	protected Set<VirtualFile> enumerateGroovyFiles(final Module module)
	{
		final Set<VirtualFile> moduleClasses = new HashSet<VirtualFile>();
		ModuleRootManager.getInstance(module).getFileIndex().iterateContent(new ContentIterator()
		{
			@Override
			public boolean processFile(final VirtualFile vfile)
			{
				if(!vfile.isDirectory() && GroovyFileType.GROOVY_FILE_TYPE.equals(vfile.getFileType()))
				{
					AccessRule.read(() ->
					{
						if(PsiManager.getInstance(myProject).findFile(vfile) instanceof GroovyFile)
						{
							moduleClasses.add(vfile);
						}
					});
				}
				return true;
			}
		});
		return moduleClasses;
	}

	protected static void addStubsToCompileScope(List<String> outputPaths, CompileContext compileContext, Module module)
	{
		List<VirtualFile> stubFiles = new ArrayList<VirtualFile>();
		for(String outputPath : outputPaths)
		{
			final File stub = new File(outputPath);
			CompilerUtil.refreshIOFile(stub);
			final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(stub);
			ContainerUtil.addIfNotNull(file, stubFiles);
		}
		((CompileContextEx) compileContext).addScope(new FileSetCompileScope(stubFiles, new Module[]{module}));
	}

	@javax.annotation.Nullable
	protected static VirtualFile getMainOutput(CompileContext compileContext, Module module, boolean tests)
	{
		return tests ? compileContext.getModuleOutputDirectoryForTests(module) : compileContext.getModuleOutputDirectory(module);
	}

	private static CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage)
	{
		String category = compilerMessage.getCategory();

		if(category.equals(GroovyCompilerMessageCategories.ERROR))
		{
			return CompilerMessageCategory.ERROR;
		}
		if(category.equals(GroovyCompilerMessageCategories.INFORMATION))
		{
			return CompilerMessageCategory.INFORMATION;
		}
		if(category.equals(GroovyCompilerMessageCategories.WARNING))
		{
			return CompilerMessageCategory.WARNING;
		}
		if(category.equals(GroovyCompilerMessageCategories.STATISTICS))
		{
			return CompilerMessageCategory.STATISTICS;
		}

		return CompilerMessageCategory.ERROR;
	}

	private static void appendOutputPath(Module module, PathsList compileClasspath, final boolean forTestClasses)
	{
		String output = CompilerPathsImpl.getModuleOutputPath(module, forTestClasses);
		if(output != null)
		{
			compileClasspath.add(FileUtil.toSystemDependentName(output));
		}
	}

	private static ModuleChunk createChunk(Module module, CompileContext context)
	{
		return new ModuleChunk((CompileContextEx) context, new Chunk<Module>(module), Collections.<Module, List<VirtualFile>>emptyMap());
	}

	@Override
	public void compile(final CompileContext compileContext, Chunk<Module> moduleChunk, final VirtualFile[] virtualFiles, OutputSink sink)
	{
		Map<Module, List<VirtualFile>> mapModulesToVirtualFiles;
		if(moduleChunk.getNodes().size() == 1)
		{
			mapModulesToVirtualFiles = Collections.singletonMap(moduleChunk.getNodes().iterator().next(), Arrays.asList(virtualFiles));
		}
		else
		{
			mapModulesToVirtualFiles = CompilerUtil.buildModuleToFilesMap(compileContext, virtualFiles);
		}
		for(final Module module : moduleChunk.getNodes())
		{
			final GroovyModuleExtension extension = ModuleUtilCore.getExtension(module, GroovyModuleExtension.class);
			if(extension == null)
			{
				continue;
			}

			final List<VirtualFile> moduleFiles = mapModulesToVirtualFiles.get(module);
			if(moduleFiles == null)
			{
				continue;
			}

			final ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();
			final List<VirtualFile> toCompile = new ArrayList<VirtualFile>();
			final List<VirtualFile> toCompileTests = new ArrayList<VirtualFile>();
			final PsiManager psiManager = PsiManager.getInstance(myProject);

			for(final VirtualFile file : moduleFiles)
			{
				if(shouldCompile(file, psiManager))
				{
					(index.isInTestSourceContent(file) ? toCompileTests : toCompile).add(file);
				}
			}

			if(!toCompile.isEmpty())
			{
				compileFiles(compileContext, module, toCompile, sink, false);
			}
			if(!toCompileTests.isEmpty())
			{
				compileFiles(compileContext, module, toCompileTests, sink, true);
			}

		}

	}

	private static boolean shouldCompile(final VirtualFile file, final PsiManager manager)
	{
		if(ResourceCompilerConfiguration.getInstance(manager.getProject()).isResourceFile(file))
		{
			return false;
		}

		final FileType fileType = file.getFileType();
		if(fileType == GroovyFileType.GROOVY_FILE_TYPE)
		{
			return AccessRule.read(() ->
			{
				PsiFile psiFile = manager.findFile(file);
				if(psiFile instanceof GroovyFile && ((GroovyFile) psiFile).isScript())
				{
					final GroovyScriptType scriptType = GroovyScriptUtil.getScriptType((GroovyFile) psiFile);
					return scriptType.shouldBeCompiled((GroovyFile) psiFile);
				}
				return true;
			});
		}

		return fileType == JavaFileType.INSTANCE;
	}

	protected abstract void compileFiles(CompileContext compileContext, Module module, List<VirtualFile> toCompile, OutputSink sink, boolean tests);

	@Override
	public boolean isCompilableFile(VirtualFile file, CompileContext context)
	{
		final boolean result = GroovyFileType.GROOVY_FILE_TYPE.equals(file.getFileType());
		if(result && LOG.isDebugEnabled())
		{
			LOG.debug("compilable file: " + file.getPath());
		}
		return result;
	}
}
