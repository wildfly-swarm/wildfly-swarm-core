/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.asset.FileAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.impl.base.asset.ZipFileEntryAsset;
import org.jboss.shrinkwrap.impl.base.io.IOUtil;
import org.wildfly.swarm.bootstrap.Main;
import org.wildfly.swarm.bootstrap.env.WildFlySwarmManifest;
import org.wildfly.swarm.bootstrap.util.BootstrapProperties;
import org.wildfly.swarm.bootstrap.util.MavenArtifactDescriptor;

/**
 * @author Bob McWhirter
 * @author Heiko Braun
 */
public class BuildTool {

    public enum FractionDetectionMode { when_missing, force, never }

    public BuildTool(ArtifactResolvingHelper resolvingHelper) {
        this.archive = ShrinkWrap.create(JavaArchive.class);
        this.resolver = new DefaultArtifactResolver(resolvingHelper);
        this.dependencyManager = new DependencyManager(resolver);
    }

    public BuildTool declaredDependencies(DeclaredDependencies declaredDependencies) {
        this.declaredDependencies = declaredDependencies;
        return this;
    }

    public BuildTool mainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    public BuildTool properties(Properties properties) {
        this.properties.putAll(properties);
        return this;
    }

    public BuildTool bundleDependencies(boolean bundleDependencies) {
        this.bundleDependencies = bundleDependencies;
        return this;
    }

    public BuildTool projectArtifact(String groupId, String artifactId, String version, String packaging, File file) {
        projectArtifact(groupId, artifactId, version, packaging, file, null);

        return this;
    }

    public BuildTool projectArtifact(String groupId, String artifactId, String version,
                                     String packaging, File file, String artifactName) {
        this.projectAsset = new ArtifactAsset(new ArtifactSpec(null, groupId, artifactId, version, packaging, null, file),
                                              artifactName);
        this.dependencyManager.setProjectAsset(this.projectAsset);
        return this;
    }

    public BuildTool projectArchive(Archive archive) {
        this.projectAsset = new ArchiveAsset(archive);
        this.dependencyManager.setProjectAsset(this.projectAsset);
        return this;
    }

    public BuildTool fraction(ArtifactSpec spec) {
        this.fractions.add(spec);

        return this;
    }

    public BuildTool additionalModule(String module) {
        this.additionalModules.add(module);

        return this;
    }

    public BuildTool additionalModules(Collection<String> modules) {
        this.additionalModules.addAll(modules);

        return this;
    }


    public BuildTool resourceDirectory(String dir) {
        this.resourceDirectories.add(dir);
        return this;
    }

    public BuildTool fractionList(FractionList v) {
        this.fractionList = v;

        return this;
    }

    public BuildTool fractionDetectionMode(FractionDetectionMode v) {
        this.fractionDetectionMode = v;

        return this;
    }

    public BuildTool executable(boolean executable) {
        this.executable = executable;

        return this;
    }

    public BuildTool executableScript(File executableScript) {
        this.executableScript = executableScript;

        return this;
    }

    public BuildTool hollow(boolean hollow) {
        this.hollow = hollow;
        return this;
    }

    public BuildTool logger(SimpleLogger logger) {
        this.log = logger;

        return this;
    }

    public File build(String baseName, Path dir) throws Exception {
        build();
        return createJar(baseName, dir);
    }

    public void repackageWar(File file) throws IOException {
        this.log.info("Repackaging .war: " + file);

        Path backupPath = get(file);
        move(file, backupPath, this.log);

        Archive original = ShrinkWrap.create(JavaArchive.class);
        original.as(ZipImporter.class).importFrom(backupPath.toFile());

        WebInfLibFilteringArchive repackaged = new WebInfLibFilteringArchive(original, this.dependencyManager);
        repackaged.as(ZipExporter.class).exportTo(file, true);
        this.log.info("Repackaged .war: " + file);
    }

    private static synchronized Path get(File file) {
        return Paths.get(file.toString() + ".original");
    }

    private static synchronized void move(File file, Path backupPath, SimpleLogger log) throws IOException {

        final Path path = file.toPath();

        try {
            Files.move(path, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.info("Fallback file move: " + file.getAbsolutePath());
            //Fallback strategy - Create the backup and delete target path
            Files.copy(path, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            log.info("Copied " + path + " to " + backupPath);
            try {
                Files.deleteIfExists(path);
            } catch (IOException del) {
                log.info("Fallback failed to delete, will overwrite existing file: " + file.getAbsolutePath());
            }
        }
    }

    public Archive build() throws Exception {
        if (null == declaredDependencies) {
            throw new IllegalStateException("Dependency declaration is not provided!");
        }

        analyzeDependencies(false);
        addWildflySwarmBootstrapJar();
        addJarManifest();
        addWildFlySwarmApplicationManifest();
        addAdditionalModules();
        addProjectAsset((ResolvedDependencies) this.dependencyManager);
        populateUberJarMavenRepository((ResolvedDependencies) this.dependencyManager);

        return this.archive;
    }

    private boolean bootstrapJarShadesJBossModules(File artifactFile) throws IOException {
        boolean jbossModulesFound = false;
        try (JarFile jarFile = new JarFile(artifactFile)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry each = entries.nextElement();
                if (each.getName().startsWith("org/jboss/modules/ModuleLoader")) {
                    jbossModulesFound = true;
                }
            }
        }
        return jbossModulesFound;
    }

    private void expandArtifact(File artifactFile) throws IOException {
        JarFile jarFile = new JarFile(artifactFile);
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
            JarEntry each = entries.nextElement();
            if (each.getName().startsWith("META-INF")) {
                continue;
            }
            if (each.isDirectory()) {
                continue;
            }
            this.archive.add(new ZipFileEntryAsset(jarFile, each), each.getName());
        }
    }

    private void analyzeDependencies(boolean autodetect) throws Exception {
        if (null == declaredDependencies) {
            throw new IllegalStateException("dependency declaration is not provided");
        }

        this.dependencyManager.analyzeDependencies(autodetect, declaredDependencies);
    }

    private void addProjectAsset(ResolvedDependencies resolvedDependencies) {
        if (this.hollow) {
            return;
        }
        this.archive.add(new WebInfLibFilteringArchiveAsset(this.projectAsset, this.dependencyManager));
    }

    private boolean detectFractions(ResolvedDependencies resolvedDependencies) throws Exception {
        final File tmpFile = File.createTempFile("buildtool", this.projectAsset.getName().replace("/", "_"));
        tmpFile.deleteOnExit();
        this.projectAsset.getArchive().as(ZipExporter.class).exportTo(tmpFile, true);
        final FractionUsageAnalyzer analyzer = new FractionUsageAnalyzer(this.fractionList)
                .logger(log)
                .source(tmpFile);

        resolvedDependencies.getDependencies().stream()
                .filter(d -> !"provided".equals(d.scope) && !"test".equals(d.scope))
                .filter(d -> this.fractionList.getFractionDescriptor(d.groupId(), d.artifactId()) == null)
                .forEach(d -> analyzer.source(d.file));

        final Set<FractionDescriptor> detectedFractions = analyzer.detectNeededFractions();

        //don't overwrite fractions added by the user
        detectedFractions.removeAll(this.fractions.stream()
                                            .map(x -> FractionDescriptor.fromArtifactSpec(x))
                                            .collect(Collectors.toSet()));

        this.log.info(String.format("Detected %sfractions: %s",
                                    this.fractions.isEmpty() ? "" : "additional ",
                                    String.join(", ",
                                                detectedFractions.stream()
                                                        .map(FractionDescriptor::av)
                                                        .sorted()
                                                        .collect(Collectors.toList()))));
        detectedFractions.stream()
                .map(FractionDescriptor::toArtifactSpec)
                .forEach(this::fraction);

        return !detectedFractions.isEmpty();
    }

    static String strippedSwarmGav(MavenArtifactDescriptor desc) {
        if (desc.groupId().equals(DependencyManager.WILDFLY_SWARM_GROUP_ID)) {
            return String.format("%s:%s", desc.artifactId(), desc.version());
        }

        return desc.mscGav();
    }

    private void addFractions(ResolvedDependencies resolvedDependencies) throws Exception {
        final Set<ArtifactSpec> allFractions = new HashSet<>(this.fractions);
        this.fractions.stream()
                .flatMap(s -> this.fractionList.getFractionDescriptor(s.groupId(), s.artifactId())
                        .getDependencies()
                        .stream()
                        .map(FractionDescriptor::toArtifactSpec))
                .filter(d -> resolvedDependencies.findArtifact(d.groupId(), d.artifactId(), null, null, null) == null)
                .forEach(allFractions::add);

        this.log.info("Adding fractions: " +
                              String.join(", ", allFractions.stream()
                                      .map(BuildTool::strippedSwarmGav)
                                      .sorted()
                                      .collect(Collectors.toList())));

        allFractions.forEach(f -> this.declaredDependencies.add(f));
        analyzeDependencies(true);
    }

    private void addWildflySwarmBootstrapJar() throws Exception {

        ResolvedDependencies resolvedDependencies = (ResolvedDependencies) this.dependencyManager;

        ArtifactSpec artifact = resolvedDependencies.findWildFlySwarmBootstrapJar();

        if (this.fractionDetectionMode != FractionDetectionMode.never) {

            assert fractionList != null : "No FractionList provided";

            if (this.fractionDetectionMode == FractionDetectionMode.force || artifact == null) {
                this.log.info("Scanning for needed WildFly Swarm fractions with mode: " + this.fractionDetectionMode);

                if (detectFractions(resolvedDependencies)) {
                    addFractions(resolvedDependencies);
                }
            }
        } else {
            // Ensure user added fractions have dependencies resolved when FractionDetectionMode.never
            if (!this.fractions.isEmpty()) {
                addFractions(resolvedDependencies);
            }
        }

        artifact = this.dependencyManager.findWildFlySwarmBootstrapJar();

        if (this.fractionDetectionMode == FractionDetectionMode.never &&
                artifact == null) {
            this.log.error("No WildFly Swarm dependencies found and fraction detection disabled");
        }

        if (artifact != null) {
            if (!bootstrapJarShadesJBossModules(artifact.file)) {
                ArtifactSpec jbossModules = this.dependencyManager.findJBossModulesJar();
                expandArtifact(jbossModules.file);
            }
            expandArtifact(artifact.file);
        } else {
            throw new IllegalStateException("No WildFly Swarm Bootstrap fraction found");
        }
    }

    private static final DateFormat ISO_DATE = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'"); // Quoted "Z" to indicate UTC, no timezone offset

    static {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        ISO_DATE.setTimeZone(tz);
    }

    private void addJarManifest() {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, Main.class.getName());

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            manifest.write(out);
            out.close();
            byte[] bytes = out.toByteArray();
            this.archive.addAsManifestResource(new ByteArrayAsset(bytes), "MANIFEST.MF");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addWildFlySwarmApplicationManifest() {
        WildFlySwarmManifest manifest = this.dependencyManager.getWildFlySwarmManifest();

        String timestamp = ISO_DATE.format(new Date());
        this.properties.put("swarm.uberjar.build.timestamp", timestamp);
        this.properties.put("swarm.uberjar.build.user", System.getProperty("user.name"));
        if (!this.hollow) {
            this.properties.put(BootstrapProperties.APP_ARTIFACT, this.projectAsset.getSimpleName());
        }

        manifest.setProperties(this.properties);
        manifest.bundleDependencies(this.bundleDependencies);
        manifest.setMainClass(this.mainClass);
        manifest.setHollow(this.hollow);
        this.archive.add(new StringAsset(manifest.toString()), WildFlySwarmManifest.CLASSPATH_LOCATION);

    }

    private File createJar(String baseName, Path dir) throws IOException {
        File out = new File(dir.toFile(), baseName + "-swarm.jar");
        if (!out.getParentFile().exists() && !out.getParentFile().mkdirs()) {
            this.log.error("Failed to create parent directory for: " + out.getAbsolutePath());
        }
        ZipExporter exporter = this.archive.as(ZipExporter.class);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            if (executable) {
                try (InputStream is = getLaunchScript()) {
                    IOUtil.copy(is, fos);
                }
            }
            exporter.exportTo(fos);
        }
        if (executable) {
            if (!out.setExecutable(true)) {
                this.log.error("Failed to set executable flag");
            }
        }
        return out;
    }

    private InputStream getLaunchScript() throws IOException {
        return (executableScript != null) ? new FileInputStream(executableScript) :
                getClass().getResourceAsStream("launch.sh");
    }


    private void addAdditionalModules() throws IOException {
        for (String additionalModule : additionalModules) {
            final File moduleDir = new File(additionalModule);
            this.archive.addAsResource(moduleDir, "modules");
            find(moduleDir, dependencyManager);

        }
    }

    private static synchronized void find(File moduleDir, DependencyManager dependencyManager) throws IOException {
        Files.find(moduleDir.toPath(), 20,
                   (p, __) -> p.getFileName().toString().equals("module.xml"))
                .forEach(dependencyManager::addAdditionalModule);
    }

    private void populateUberJarMavenRepository(ResolvedDependencies resolvedDependencies) throws Exception {
        if (this.bundleDependencies) {
            populateUberJarMavenRepository(this.archive, resolvedDependencies);
        } else {
            populateUserMavenRepository(resolvedDependencies);
        }
    }

    private void populateUberJarMavenRepository(Archive archive, ResolvedDependencies resolvedDependencies) throws Exception {

        Set<ArtifactSpec> alreadyResolved = new HashSet<>();
        List<ArtifactSpec> toBeResolved = new ArrayList<>();

        for (ArtifactSpec dependency : resolvedDependencies.getDependencies()) {

            boolean unresolved = !dependency.isResolved();
            boolean exploded = ResolvedDependencies.isExplodedBootstrap(dependency);

            if (unresolved || !exploded) {
                toBeResolved.add(dependency);
            } else if (!unresolved) {
                alreadyResolved.add(dependency);
            }
        }

        for (ArtifactSpec dependency : resolvedDependencies.getModuleDependencies()) {
            if (!dependency.isResolved()) {
                toBeResolved.add(dependency);
            } else {
                alreadyResolved.add(dependency);
            }
        }

        System.out.println("Resolving " + toBeResolved.size() + " out of " +
                                   (resolvedDependencies.getModuleDependencies().size() +
                                           resolvedDependencies.getDependencies().size()) + " artifacts");

        if (toBeResolved.size() > 0) {
            Collection<ArtifactSpec> newResolved = resolver.resolveAllArtifactsNonTransitively(toBeResolved);
            alreadyResolved.addAll(newResolved);
        }

        for (ArtifactSpec dependency : alreadyResolved) {
            addArtifactToArchiveMavenRepository(archive, dependency);
        }
    }

    private void populateUserMavenRepository(ResolvedDependencies resolvedDependencies) throws Exception {
        List<ArtifactSpec> toBeResolved = new ArrayList<>();

        toBeResolved.addAll(
                resolvedDependencies.getDependencies().stream()
                        .filter(a -> a.isResolved() == false)
                        .collect(Collectors.toList())
        );
        toBeResolved.addAll(
                resolvedDependencies.getModuleDependencies().stream()
                        .filter(a -> a.isResolved() == false)
                        .collect(Collectors.toList())
        );

        System.out.println("Resolving " + toBeResolved.size() + " out of " +
                                   (resolvedDependencies.getModuleDependencies().size() +
                                           resolvedDependencies.getDependencies().size()) + " artifacts");

        if (toBeResolved.size() > 0) {
            resolver.resolveAllArtifactsNonTransitively(toBeResolved);
        }
    }

    private void addArtifactToArchiveMavenRepository(Archive archive, ArtifactSpec artifact) throws Exception {
        if (!artifact.isResolved()) {
            throw new IllegalArgumentException("Artifact should be resolved!");
        }

        StringBuilder artifactPath = new StringBuilder("m2repo/");
        artifactPath.append(artifact.repoPath(true));

        archive.add(new FileAsset(artifact.file), artifactPath.toString());
    }

    private final Set<ArtifactSpec> fractions = new HashSet<>();

    private final JavaArchive archive;

    private final Set<String> resourceDirectories = new HashSet<>();

    private String mainClass;

    private boolean bundleDependencies = true;

    private boolean executable;

    private File executableScript;

    private DependencyManager dependencyManager;

    private ProjectAsset projectAsset;

    private Properties properties = new Properties();

    private Set<String> additionalModules = new HashSet<>();

    private FractionDetectionMode fractionDetectionMode = FractionDetectionMode.when_missing;

    private FractionList fractionList = null;

    private SimpleLogger log = STD_LOGGER;

    private boolean hollow;

    private DeclaredDependencies declaredDependencies;

    private final DefaultArtifactResolver resolver;

    public static final SimpleLogger STD_LOGGER = new SimpleLogger() {
        @Override
        public void debug(String msg) {
        }

        @Override
        public void info(String msg) {
            System.out.println(msg);
        }

        @Override
        public void error(String msg) {
            System.err.println(msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            error(msg);
            t.printStackTrace();
        }
    };

    public static final SimpleLogger STD_LOGGER_WITH_DEBUG = new SimpleLogger() {
        @Override
        public void debug(String msg) {
            System.out.println(msg);
        }

        @Override
        public void info(String msg) {
            System.out.println(msg);
        }

        @Override
        public void error(String msg) {
            System.err.println(msg);
        }

        @Override
        public void error(String msg, Throwable t) {
            error(msg);
            t.printStackTrace();
        }
    };

    public static final SimpleLogger NOP_LOGGER = new SimpleLogger() {
        @Override
        public void debug(String msg) {
        }

        @Override
        public void info(String msg) {
        }

        @Override
        public void error(String msg) {
        }

        @Override
        public void error(String msg, Throwable t) {
        }
    };

    public interface SimpleLogger {
        void debug(String msg);

        void info(String msg);

        void error(String msg);

        void error(String msg, Throwable t);
    }
}

