package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Concrete adapter for the verified Chunker CLI JAR contract. */
public final class ChunkerCliAdapter implements ConverterAdapter {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9._-]+)?)(?!\\d)");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\b(?:JAVA|BEDROCK)_[A-Z0-9_]+\\b");

    private final Path javaExecutable;
    private final int maxHeapMb;
    private final Duration probeTimeout;
    private final Duration conversionTimeout;
    private final OnDemandProcessRunner processRunner;

    public ChunkerCliAdapter(
            Path javaExecutable,
            int maxHeapMb,
            Duration probeTimeout,
            Duration conversionTimeout,
            OnDemandProcessRunner processRunner
    ) {
        this.javaExecutable = Objects.requireNonNull(javaExecutable, "javaExecutable").toAbsolutePath().normalize();
        if (maxHeapMb < 512) throw new IllegalArgumentException("maxHeapMb must be at least 512");
        this.maxHeapMb = maxHeapMb;
        this.probeTimeout = requirePositive(probeTimeout, "probeTimeout");
        this.conversionTimeout = requirePositive(conversionTimeout, "conversionTimeout");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
    }

    public static Path currentJavaExecutable() {
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toAbsolutePath().normalize();
    }

    @Override
    public ConverterProbe probe(Path runtimeArtifact) throws IOException {
        Path artifact = requireRuntimeArtifact(runtimeArtifact);
        Path workingDirectory = artifact.getParent();
        OnDemandProcessRunner.ProcessResult versionResult = run(
                List.of(javaExecutable.toString(), "-jar", artifact.toString(), "--version"),
                workingDirectory,
                probeTimeout
        );
        if (versionResult.exitCode() != 0) {
            throw new IOException("Conversion runtime version probe failed with exit code " + versionResult.exitCode());
        }
        String version = parseRuntimeVersion(versionResult.output());

        OnDemandProcessRunner.ProcessResult helpResult = run(
                List.of(javaExecutable.toString(), "-jar", artifact.toString(), "--help"),
                workingDirectory,
                probeTimeout
        );
        if (helpResult.exitCode() != 0 || !hasRequiredCliContract(helpResult.output())) {
            throw new IOException("Conversion runtime CLI contract probe failed");
        }

        Path probeRoot = Files.createTempDirectory(workingDirectory, "lazybuilder-probe-");
        try {
            Path input = Files.createDirectory(probeRoot.resolve("input"));
            Path output = probeRoot.resolve("output");
            OnDemandProcessRunner.ProcessResult formatsResult = run(
                    List.of(
                            javaExecutable.toString(), "-jar", artifact.toString(),
                            "-i", input.toString(),
                            "-f", "__LAZYBUILDER_LIST_FORMATS__",
                            "-o", output.toString()
                    ),
                    probeRoot,
                    probeTimeout
            );
            List<String> formats = parseSupportedFormats(formatsResult.output());
            if (formats.isEmpty()) throw new IOException("Conversion runtime did not expose a supported-format catalog");
            return new ConverterProbe(version, formats);
        } finally {
            deleteTree(probeRoot);
        }
    }

    @Override
    public ConversionResult convert(Path runtimeArtifact, ConversionRequest request) throws IOException {
        Path artifact = requireRuntimeArtifact(runtimeArtifact);
        Objects.requireNonNull(request, "request");
        if (!Files.isDirectory(request.inputDirectory())) {
            throw new IOException("Conversion input directory does not exist: " + request.inputDirectory());
        }
        if (Files.exists(request.outputDirectory())) {
            throw new IOException("Conversion output must not already exist: " + request.outputDirectory());
        }
        requireOptionalSettingsFile(request.pruningSettings(), "Pruning settings");
        requireOptionalSettingsFile(request.worldSettings(), "World settings");
        requireOptionalSettingsFile(request.converterSettings(), "Converter settings");

        List<String> command = buildConversionCommand(artifact, request);
        OnDemandProcessRunner.ProcessResult result = run(command, artifact.getParent(), conversionTimeout);
        if (result.exitCode() != 0) {
            throw new IOException("Conversion runtime exited with code " + result.exitCode() + ": " + result.output());
        }
        validateOutputDirectory(request.outputDirectory(), request.outputFormat());
        return new ConversionResult(result.output());
    }

    List<String> buildConversionCommand(Path runtimeArtifact, ConversionRequest request) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xmx" + maxHeapMb + "m");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-jar");
        command.add(runtimeArtifact.toAbsolutePath().normalize().toString());
        command.add("-i");
        command.add(request.inputDirectory().toString());
        command.add("-f");
        command.add(request.outputFormat());
        command.add("-o");
        command.add(request.outputDirectory().toString());
        addSettingsArgument(command, "-s", request.worldSettings());
        addSettingsArgument(command, "-p", request.pruningSettings());
        addSettingsArgument(command, "-c", request.converterSettings());
        if (request.keepOriginalNbt()) command.add("-k");
        return List.copyOf(command);
    }

    static void validateOutputDirectory(Path outputDirectory, String outputFormat) throws IOException {
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        String format = Objects.requireNonNull(outputFormat, "outputFormat").strip().toUpperCase(Locale.ROOT);
        if (!Files.isDirectory(output)) {
            throw new IOException("Conversion runtime reported success but produced no output directory");
        }
        Path levelDat = output.resolve("level.dat");
        if (!Files.isRegularFile(levelDat) || Files.size(levelDat) == 0L) {
            throw new IOException("Conversion runtime produced an incomplete world: level.dat is missing or empty");
        }
        if (format.startsWith("BEDROCK_") && !Files.isDirectory(output.resolve("db"))) {
            throw new IOException("Conversion runtime produced an incomplete Bedrock world: db directory is missing");
        }
    }

    static String parseRuntimeVersion(String output) throws IOException {
        Matcher matcher = VERSION_PATTERN.matcher(Objects.requireNonNull(output, "output"));
        if (!matcher.find()) throw new IOException("Could not parse conversion runtime version");
        return matcher.group(1);
    }

    static List<String> parseSupportedFormats(String output) {
        Matcher matcher = FORMAT_PATTERN.matcher(Objects.requireNonNull(output, "output").toUpperCase(Locale.ROOT));
        TreeSet<String> formats = new TreeSet<>();
        while (matcher.find()) formats.add(matcher.group());
        return List.copyOf(formats);
    }

    static boolean hasRequiredCliContract(String output) {
        String value = Objects.requireNonNull(output, "output");
        return value.contains("--inputDirectory")
                && value.contains("--outputFormat")
                && value.contains("--outputDirectory")
                && value.contains("--worldSettings")
                && value.contains("--pruning")
                && value.contains("--converterSettings")
                && value.contains("--keepOriginalNBT");
    }

    private static void requireOptionalSettingsFile(Path path, String label) throws IOException {
        if (path != null && !Files.isRegularFile(path)) {
            throw new IOException(label + " file does not exist: " + path);
        }
    }

    private static void addSettingsArgument(List<String> command, String option, Path path) {
        if (path == null) return;
        command.add(option);
        command.add(path.toString());
    }

    private Path requireRuntimeArtifact(Path runtimeArtifact) throws IOException {
        Path artifact = Objects.requireNonNull(runtimeArtifact, "runtimeArtifact").toAbsolutePath().normalize();
        if (!Files.isRegularFile(artifact)) throw new IOException("Conversion runtime artifact is missing: " + artifact);
        return artifact;
    }

    private OnDemandProcessRunner.ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) throws IOException {
        try {
            return processRunner.run(command, workingDirectory, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Conversion runtime execution was interrupted", interrupted);
        }
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return duration;
    }

    private static void deleteTree(Path root) throws IOException {
        if (Files.notExists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
