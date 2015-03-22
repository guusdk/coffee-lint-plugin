package com.coffeelint.cli;

import com.google.common.base.Charsets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.wix.nodejs.NodeRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class CoffeeLintRunner {
    private CoffeeLintRunner() {
    }

    private static final Logger LOG = Logger.getInstance(CoffeeLintRunner.class);

    private static final int TIME_OUT = (int) TimeUnit.SECONDS.toMillis(120L);


    public static class CoffeeLintSettings {
        public String node;
        public String eslintExecutablePath;
        public String rules;
        public String config;
        public String cwd;
        public String targetFile;
    }

    public static CoffeeLintSettings buildSettings(@NotNull String cwd, @NotNull String path, @NotNull String nodeInterpreter, @NotNull String eslintBin, @Nullable String eslintrc, @Nullable String rulesdir) {
        CoffeeLintSettings settings = new CoffeeLintSettings();
        settings.cwd = cwd;
        settings.eslintExecutablePath = eslintBin;
        settings.node = nodeInterpreter;
        settings.rules = rulesdir;
        settings.config = eslintrc;
        settings.targetFile = path;
        return settings;
    }

//    @NotNull
//    public static ProcessOutput lint(@NotNull CoffeeLintSettings settings) throws ExecutionException {
//        GeneralCommandLine commandLine = createCommandLineLint(settings);
//        return execute(commandLine, TIME_OUT);
//    }

    public static LintResult lint(String cwd, String file, String node, String lintBin, String eslintRcFile, String customRulesPath) {
        return lint(buildSettings(cwd, file, node, lintBin, eslintRcFile, customRulesPath));
    }

    public static LintResult lint(@NotNull CoffeeLintSettings settings) {
        LintResult result = new LintResult();
        try {
            GeneralCommandLine commandLine = createCommandLineLint(settings);
            commandLine.addParameter("--reporter");
            commandLine.addParameter("checkstyle");
            ProcessOutput out = execute(commandLine, TIME_OUT);
            if (out.getExitCode() != 0) {
                result.errorOutput = out.getStderr();
                try {
                    result.coffeeLint = CoffeeLint.read(out.getStdout());
                } catch (Exception e) {
                    LOG.error(e);
                    //result.errorOutput = out.getStdout();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.errorOutput = e.toString();
        }
        return result;
    }

    @NotNull
    private static ProcessOutput version(@NotNull CoffeeLintSettings settings) throws ExecutionException {
        GeneralCommandLine commandLine = createCommandLine(settings);
        commandLine.addParameter("-v");
        return execute(commandLine, TIME_OUT);
    }

    @NotNull
    public static String runVersion(@NotNull CoffeeLintSettings settings) throws ExecutionException {
        if (!new File(settings.eslintExecutablePath).exists()) {
            LOG.warn("Calling version with invalid coffeelint exe " + settings.eslintExecutablePath);
            return "";
        }
        ProcessOutput out = version(settings);
        if (out.getExitCode() == 0) {
            return out.getStdout().trim();
        }
        return "";
    }

    @NotNull
    private static GeneralCommandLine createCommandLine(@NotNull CoffeeLintSettings settings) {
        return NodeRunner.createCommandLine(settings.cwd, settings.node, settings.eslintExecutablePath);
    }

    @NotNull
    private static GeneralCommandLine createCommandLineLint(@NotNull CoffeeLintSettings settings) {
        GeneralCommandLine commandLine = createCommandLine(settings);
        // TODO validate arguments (file exist etc)
        commandLine.addParameter(settings.targetFile);
        if (StringUtil.isNotEmpty(settings.config)) {
            commandLine.addParameter("-c");
            commandLine.addParameter(settings.config);
        }
        if (StringUtil.isNotEmpty(settings.rules)) {
            commandLine.addParameter("--rulesdir");
            commandLine.addParameter("['" + settings.rules + "']");
        }
        return commandLine;
    }

    @NotNull
    private static ProcessOutput execute(@NotNull GeneralCommandLine commandLine, int timeoutInMilliseconds) throws ExecutionException {
        LOG.info("Running coffeelint command: " + commandLine.getCommandLineString());
        Process process = commandLine.createProcess();
        OSProcessHandler processHandler = new ColoredProcessHandler(process, commandLine.getCommandLineString(), Charsets.UTF_8);
        final ProcessOutput output = new ProcessOutput();
        processHandler.addProcessListener(new ProcessAdapter() {
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                if (outputType.equals(ProcessOutputTypes.STDERR)) {
                    output.appendStderr(event.getText());
                } else if (!outputType.equals(ProcessOutputTypes.SYSTEM)) {
                    output.appendStdout(event.getText());
                }
            }
        });
        processHandler.startNotify();
        if (processHandler.waitFor(timeoutInMilliseconds)) {
            output.setExitCode(process.exitValue());
        } else {
            processHandler.destroyProcess();
            output.setTimeout();
        }
        if (output.isTimeout()) {
            throw new ExecutionException("Command '" + commandLine.getCommandLineString() + "' is timed out.");
        }
        return output;
    }
}