/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.impl.runner;

import consulo.logging.Logger;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.local.ProcessHandlerFactory;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovyCompilerMessageCategories;
import org.jetbrains.groovy.compiler.rt.GroovyRtConstants;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Dmitry.Krasilschikov
 * @since 16.04.2007
 */
public class GroovycOSProcessHandler implements ProcessListener {
  public static final String GROOVY_COMPILER_IN_OPERATION = "Groovy compiler in operation...";
  public static final String GRAPE_ROOT = "grape.root";
  private final List<OutputItem> myCompiledItems = new ArrayList<OutputItem>();
  private final Set<File> toRecompileFiles = new HashSet<File>();
  private final List<CompilerMessage> compilerMessages = new ArrayList<CompilerMessage>();
  private final StringBuffer stdErr = new StringBuffer();

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.groovy.GroovycOSProcessHandler");
  private final Consumer<String> myStatusUpdater;

  private ProcessHandler myProcessHandler;

  public GroovycOSProcessHandler(GeneralCommandLine commandLine, Consumer<String> statusUpdater) throws ExecutionException {
    myProcessHandler = ProcessHandlerFactory.getInstance().createProcessHandler(commandLine);
    myProcessHandler.addProcessListener(this);
    myStatusUpdater = statusUpdater;
  }

  public void waitFor() {
    myProcessHandler.waitFor();
  }

  public void startNotify() {
    myProcessHandler.startNotify();
  }

  @Override
  public void onTextAvailable(ProcessEvent event, Key outputType) {
    String text = event.getText();

    if (LOG.isDebugEnabled()) {
      LOG.debug("Received from groovyc: " + text);
    }

    if (outputType == ProcessOutputTypes.SYSTEM) {
      return;
    }

    if (outputType == ProcessOutputTypes.STDERR) {
      stdErr.append(StringUtil.convertLineSeparators(text));
      return;
    }


    parseOutput(text);
  }

  private final StringBuffer outputBuffer = new StringBuffer();

  protected void updateStatus(@Nullable String status) {
    myStatusUpdater.accept(status == null ? GROOVY_COMPILER_IN_OPERATION : status);
  }

  private void parseOutput(String text) {
    final String trimmed = text.trim();

    if (trimmed.startsWith(GroovyRtConstants.PRESENTABLE_MESSAGE)) {
      updateStatus(trimmed.substring(GroovyRtConstants.PRESENTABLE_MESSAGE.length()));
      return;
    }

    if (GroovyRtConstants.CLEAR_PRESENTABLE.equals(trimmed)) {
      updateStatus(null);
      return;
    }


    if (StringUtil.isNotEmpty(text)) {
      outputBuffer.append(text);

      //compiled start marker have to be in the beginning on each string
      if (outputBuffer.indexOf(GroovyRtConstants.COMPILED_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.COMPILED_END) == -1) {
          return;
        }

        final String compiled = handleOutputBuffer(GroovyRtConstants.COMPILED_START, GroovyRtConstants.COMPILED_END);
        final List<String> list = splitAndTrim(compiled);
        String outputPath = list.get(0);
        String sourceFile = list.get(1);

        OutputItem item = new OutputItem(outputPath, sourceFile);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Output: " + item);
        }
        myCompiledItems.add(item);

      }
      else if (outputBuffer.indexOf(GroovyRtConstants.TO_RECOMPILE_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.TO_RECOMPILE_END) != -1) {
          String url = handleOutputBuffer(GroovyRtConstants.TO_RECOMPILE_START, GroovyRtConstants.TO_RECOMPILE_END);
          toRecompileFiles.add(new File(url));
        }
      }
      else if (outputBuffer.indexOf(GroovyRtConstants.MESSAGES_START) != -1) {
        if (outputBuffer.indexOf(GroovyRtConstants.MESSAGES_END) == -1) {
          return;
        }

        text = handleOutputBuffer(GroovyRtConstants.MESSAGES_START, GroovyRtConstants.MESSAGES_END);

        List<String> tokens = splitAndTrim(text);
        LOG.assertTrue(tokens.size() > 4, "Wrong number of output params");

        String category = tokens.get(0);
        String message = tokens.get(1);
        String url = tokens.get(2);
        String lineNum = tokens.get(3);
        String columnNum = tokens.get(4);

        int lineInt;
        int columnInt;

        try {
          lineInt = Integer.parseInt(lineNum);
          columnInt = Integer.parseInt(columnNum);
        }
        catch (NumberFormatException e) {
          LOG.error(e);
          lineInt = 0;
          columnInt = 0;
        }

        CompilerMessage compilerMessage = new CompilerMessage(category, message, url, lineInt, columnInt);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Message: " + compilerMessage);
        }

        compilerMessages.add(compilerMessage);
      }
    }
  }

  private String handleOutputBuffer(String startMarker, String endMarker) {
    final int start = outputBuffer.indexOf(startMarker);
    final int end = outputBuffer.indexOf(endMarker);
    if (start > end) {
      throw new AssertionError("Malformed Groovyc output: " + outputBuffer.toString());
    }

    String text = outputBuffer.substring(start + startMarker.length(), end);

    outputBuffer.delete(start, end + endMarker.length());

    return text.trim();
  }

  private static List<String> splitAndTrim(String compiled) {
    return ContainerUtil.map(StringUtil.split(compiled, GroovyRtConstants.SEPARATOR), s -> s.trim());
  }

  public List<OutputItem> getSuccessfullyCompiled() {
    return myCompiledItems;
  }

  public Set<File> getToRecompileFiles() {
    return toRecompileFiles;
  }

  public boolean shouldRetry() {
    Integer exitCode = myProcessHandler.getExitCode();
    if (exitCode != null && exitCode != 0) {
      return true;
    }
    for (CompilerMessage message : compilerMessages) {
      if (message.getCategory().equals(GroovyCompilerMessageCategories.ERROR)) {
        return true;
      }
    }
    if (getStdErr().length() > 0) {
      return true;
    }
    return false;
  }

  public List<CompilerMessage> getCompilerMessages(String moduleName) {
    ArrayList<CompilerMessage> messages = new ArrayList<CompilerMessage>(compilerMessages);
    final StringBuffer unparsedBuffer = getStdErr();
    if (unparsedBuffer.length() != 0) {
      String msg = unparsedBuffer.toString();
      if (msg.contains(GroovyRtConstants.NO_GROOVY)) {
        msg = "Cannot compile Groovy files: no Groovy library is defined for module '" + moduleName + "'";
      }

      messages.add(new CompilerMessage(GroovyCompilerMessageCategories.INFORMATION, msg, null, -1, -1));
    }

    final int exitValue = myProcessHandler.getExitCode();
    if (exitValue != 0) {
      for (CompilerMessage message : messages) {
        if (message.getCategory().equals(GroovyCompilerMessageCategories.ERROR)) {
          return messages;
        }
      }
      messages.add(new CompilerMessage(GroovyCompilerMessageCategories.ERROR, "Internal groovyc error: code " + exitValue, null, -1, -1));
    }

    return messages;
  }

  public StringBuffer getStdErr() {
    return stdErr;
  }

  public static File fillFileWithGroovycParameters(final String outputDir,
                                                   final Collection<String> changedSources,
                                                   String finalOutput,
                                                   Map<String, String> class2Src,
                                                   @Nullable final String encoding,
                                                   List<String> patchers) throws IOException {
    File tempFile = FileUtil.createTempFile("ideaGroovyToCompile", ".txt", true);

    final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempFile)));
    try {
      for (String file : changedSources) {
        writer.write(GroovyRtConstants.SRC_FILE + "\n");
        writer.write(file);
        writer.write("\n");
      }

      writer.write("class2src\n");
      for (Map.Entry<String, String> entry : class2Src.entrySet()) {
        writer.write(entry.getKey() + "\n");
        writer.write(entry.getValue() + "\n");
      }
      writer.write(GroovyRtConstants.END + "\n");

      writer.write(GroovyRtConstants.PATCHERS + "\n");
      for (String patcher : patchers) {
        writer.write(patcher + "\n");
      }
      writer.write(GroovyRtConstants.END + "\n");
      if (encoding != null) {
        writer.write(GroovyRtConstants.ENCODING + "\n");
        writer.write(encoding + "\n");
      }
      writer.write(GroovyRtConstants.OUTPUTPATH + "\n");
      writer.write(outputDir);
      writer.write("\n");
      writer.write(GroovyRtConstants.FINAL_OUTPUTPATH + "\n");
      writer.write(finalOutput);
      writer.write("\n");
    }
    finally {
      writer.close();
    }
    return tempFile;
  }

  public static GroovycOSProcessHandler runGroovyc(GeneralCommandLine commandLine, Consumer<String> updater) throws ExecutionException {
    GroovycOSProcessHandler processHandler = new GroovycOSProcessHandler(commandLine, updater);

    processHandler.startNotify();
    processHandler.waitFor();
    return processHandler;
  }

  public static class OutputItem {
    public final String outputPath;
    public final String sourcePath;

    public OutputItem(String outputPath, String sourceFileName) {
      this.outputPath = outputPath;
      sourcePath = sourceFileName;
    }

    @Override
    public String toString() {
      return "OutputItem{" +
        "outputPath='" + outputPath + '\'' +
        ", sourcePath='" + sourcePath + '\'' +
        '}';
    }
  }

}
