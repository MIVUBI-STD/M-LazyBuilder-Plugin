package com.halokaryamedia.lazybuilder.world.conversion;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Concrete adapter for the verified Chunker CLI JAR contract. */
public final class ChunkerCliAdapter implements ConverterAdapter {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)(\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9._-]+)?)(?!\\d)");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\b(?:JAVA|BEDROCK)_[A-Z0-9_]+\\b");
    private static final Pattern CUSTOM_DIMENSION_ENTRY = Pattern.compile("(?:^|/)data/[^/]+/dimension/.+\\.json$");
    private static final String CUSTOM_DIMENSION_METADATA = "custom_dimensions.chunker.json";
    private static final Set<String> JAVA_CHUNK_DATA_DIRECTORIES = Set.of("region", "entities", "poi");
    private static final Set<String> VANILLA_DIMENSION_DIRECTORIES = Set.of("DIM-1", "DIM1");

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
    public boolean canConvertWithoutRuntime(ConversionRequest request) {
        return canUseLosslessNativeAreaPath(Objects.requireNonNull(request, "request"));
    }

    @Override
    public ConversionResult convertWithoutRuntime(ConversionRequest request) throws IOException {
        validateRequest(request);
        if (!canUseLosslessNativeAreaPath(request)) {
            throw new IOException("Conversion request requires the verified external runtime");
        }
        NativeJavaAreaPruner.exportSelectedArea(
                request.inputDirectory(), request.outputDirectory(), request.pruningSettings());
        validateOutputDirectory(request.outputDirectory(), request.outputFormat());
        return new ConversionResult("LazyBuilder lossless native Java Selected Area export");
    }

    @Override
    public ConversionResult convert(Path runtimeArtifact, ConversionRequest request) throws IOException {
        validateRequest(request);

        if (canUseLosslessNativeAreaPath(request)) {
            return convertWithoutRuntime(request);
        }

        Path artifact = requireRuntimeArtifact(runtimeArtifact);
        if (request.keepOriginalNbt()) {
            seedSameFormatOutput(request.inputDirectory(), request.outputDirectory());
        }

        List<String> command = buildConversionCommand(artifact, request);
        OnDemandProcessRunner.ProcessResult result = run(command, artifact.getParent(), conversionTimeout);
        if (result.exitCode() != 0) {
            throw new IOException("Conversion runtime exited with code " + result.exitCode() + ": " + result.output());
        }
        validateOutputDirectory(request.outputDirectory(), request.outputFormat());
        return new ConversionResult(result.output());
    }

    private static void validateRequest(ConversionRequest request) throws IOException {
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
        requireSupportedCustomDimensionShape(request);
    }

    static boolean canUseLosslessNativeAreaPath(ConversionRequest request) {
        Objects.requireNonNull(request, "request");
        return request.keepOriginalNbt() && request.worldSettings() == null;
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

    static void requireSupportedCustomDimensionShape(ConversionRequest request) throws IOException {
        Objects.requireNonNull(request, "request");
        Path input = request.inputDirectory();
        if (!containsCustomDimensionDefinitions(input)) return;

        if (request.pruningSettings() != null) {
            throw new IOException("Selected Area export does not yet support worlds with custom dimensions safely");
        }

        Path metadata = input.resolve(CUSTOM_DIMENSION_METADATA);
        if (!Files.isRegularFile(metadata) || Files.size(metadata) == 0L) {
            throw new IOException("World contains custom dimensions but verified Chunker custom-dimension metadata is missing");
        }
    }

    static boolean containsCustomDimensionDefinitions(Path inputDirectory) throws IOException {
        Path input = Objects.requireNonNull(inputDirectory, "inputDirectory").toAbsolutePath().normalize();
        Path datapacks = input.resolve("datapacks");
        if (!Files.isDirectory(datapacks)) return false;

        try (var children = Files.list(datapacks)) {
            for (Path pack : children.toList()) {
                if (Files.isDirectory(pack) && !Files.isSymbolicLink(pack)) {
                    try (var paths = Files.walk(pack)) {
                        boolean found = paths
                                .filter(Files::isRegularFile)
                                .map(pack::relativize)
                                .map(Path::toString)
                                .map(name -> name.replace('\\', '/'))
                                .anyMatch(name -> CUSTOM_DIMENSION_ENTRY.matcher(name).matches());
                        if (found) return true;
                    }
                    continue;
                }

                String name = pack.getFileName().toString().toLowerCase(Locale.ROOT);
                if (!Files.isRegularFile(pack) || !name.endsWith(".zip")) continue;
                try (ZipFile zip = new ZipFile(pack.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement().getName().replace('\\', '/');
                        if (CUSTOM_DIMENSION_ENTRY.matcher(entry).matches()) return true;
                    }
                }
            }
        }
        return false;
    }

    static void seedSameFormatOutput(Path inputDirectory, Path outputDirectory) throws IOException {
        Path input = Objects.requireNonNull(inputDirectory, "inputDirectory").toAbsolutePath().normalize();
        Path output = Objects.requireNonNull(outputDirectory, "outputDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(input) || Files.isSymbolicLink(input)) {
            throw new IOException("Same-format seed input is missing or unsafe: " + input);
        }
        if (Files.exists(output)) {
            throw new IOException("Same-format seed output must not already exist: " + output);
        }

        Files.walkFileTree(input, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new IOException("Symbolic links are not supported in conversion input: " + directory);
                }
                Path relative = input.relativize(directory);
                if (isJavaChunkDataDirectory(relative)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(output.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Symbolic links are not supported in conversion input: " + file);
                }
                Path relative = input.relativize(file);
                if (relative.getNameCount() == 1 && relative.getFileName().toString().equals("session.lock")) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = output.resolve(relative);
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.copy(file, target, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isJavaChunkDataDirectory(Path relative) {
        if (relative.getNameCount() == 1) {
            return JAVA_CHUNK_DATA_DIRECTORIES.contains(relative.getFileName().toString());
        }
        return relative.getNameCount() == 2
                && VANILLA_DIMENSION_DIRECTORIES.contains(relative.getName(0).toString())
                && JAVA_CHUNK_DATA_DIRECTORIES.contains(relative.getName(1).toString());
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
