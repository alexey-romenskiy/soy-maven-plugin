/*
 * Copyright (c) 2016, Alexey Romenskiy, All rights reserved.
 *
 * This file is part of soy-maven-plugin
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package codes.writeonce.maven.plugins.soy;

import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.SoyFileSetAccessor;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;
import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_RESOURCES;

@Mojo(name = "compile", defaultPhase = GENERATE_RESOURCES)
public class CompileMojo extends AbstractSoyMojo {

    private static final String JS_EXTENSION = ".js";

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final Charset TEXT_DIGEST_CHARSET = Charset.forName("UTF-8");

    @Parameter(required = true, defaultValue = "${basedir}/src/main/i18n")
    private File translations;

    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-js/soy")
    private File jsOutputDirectory;

    @Parameter(required = true, defaultValue = "${project.build.directory}/generated-sources/soy")
    private File javaOutputDirectory;

    @Parameter(required = true)
    private String javaPackage;

    @Parameter(required = true, defaultValue = "SOY_FILE_NAME")
    private JavaClassNameSource javaClassNameSource;

    @Parameter(required = true, defaultValue = "UTF-8")
    private String jsOutputCharsetName;

    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    private String javaOutputCharsetName;

    @Parameter
    private SoyJsSrcOptions jsSrcOptions;

    @Parameter(required = true, readonly = true, defaultValue = "${session}")
    protected MavenSession session;

    @Parameter(required = true, readonly = true, defaultValue = "${mojoExecution}")
    private MojoExecution execution;

    @Parameter(required = true, readonly = true, defaultValue = "${project.build.directory}")
    private File buildDirectory;

    @Parameter(required = true, defaultValue = "${project.build.directory}/soy-maven-plugin-markers")
    private File markersDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException("Error compiling templates: " + e.getMessage(), e);
        }
    }

    @Override
    protected void process() throws Exception {

        super.process();

        translations = getSafePath(translations);
        jsOutputDirectory = getSafePath(jsOutputDirectory);
        javaOutputDirectory = getSafePath(javaOutputDirectory);
        buildDirectory = getSafePath(buildDirectory);
        markersDirectory = getSafePath(markersDirectory);

        final List<Path> soyFiles = getSoyFiles();
        final List<Path> xliffFiles = getXliffFiles();

        final byte[] sourceDigestBytes = getSourceDigestBytes(soyFiles, xliffFiles);

        final boolean changed = isChanged(sourceDigestBytes);

        if (changed) {
            getLog().info("Generating JS and Java code from SOY templates.");
            generateJs(soyFiles, xliffFiles, sourceDigestBytes);
        } else {
            getLog().info("No changes detected. No JS or Java code generated from SOY templates.");
        }

        project.addCompileSourceRoot(javaOutputDirectory.getPath());
    }

    private void generateJs(List<Path> soyFiles, List<Path> xliffFiles, byte[] sourceDigestBytes)
            throws IOException, NoSuchAlgorithmException {

        final Path outputRootPath = jsOutputDirectory.toPath();

        FileUtils.deleteDirectory(jsOutputDirectory);
        Files.createDirectories(outputRootPath);

        FileUtils.deleteDirectory(javaOutputDirectory);

        final Path javaSourceOutputPath = getJavaSourceOutputPath();
        Files.createDirectories(javaSourceOutputPath);

        final SoyFileSet soyFileSet = getSoyFileSet(soyFiles);

        if (xliffFiles.isEmpty()) {
            getLog().info("No translations detected. Using default messages.");
            generateJs(soyFiles, soyFileSet, null, outputRootPath);
        } else {
            final SoyMsgBundleHandler smbh = new SoyMsgBundleHandler(new XliffMsgPlugin());
            for (final Path xliffFilePath : xliffFiles) {
                final SoyMsgBundle smb = smbh.createFromFile(translations.toPath().resolve(xliffFilePath).toFile());
                final Path outputPath = Utils.removeSuffix(outputRootPath.resolve(xliffFilePath), XLIFF_EXTENSION);
                generateJs(soyFiles, soyFileSet, smb, outputPath);
            }
        }

        final ImmutableMap<String, String> parseInfo = SoyFileSetAccessor.generateParseInfo(soyFileSet, javaPackage,
                javaClassNameSource.getValue());

        final Charset classSourceCharset;
        if (StringUtils.isEmpty(javaOutputCharsetName)) {
            classSourceCharset = Charset.defaultCharset();
            getLog().warn("Using platform encoding (" + classSourceCharset.displayName() +
                          " actually) to generate SOY sources, i.e. build is platform dependent!");
        } else {
            classSourceCharset = Charset.forName(javaOutputCharsetName);
        }

        for (final Map.Entry<String, String> entry : parseInfo.entrySet()) {
            final String classFileName = entry.getKey();
            final String classSource = entry.getValue();
            try (FileOutputStream out = new FileOutputStream(javaSourceOutputPath.resolve(classFileName).toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(out, classSourceCharset)) {
                writer.write(classSource);
            }
        }

        final byte[] targetDigestBytes = getGeneratedFilesDigest();

        final Path statusFilePath = getStatusFilePath();
        Files.createDirectories(statusFilePath.getParent());
        try (FileOutputStream out = new FileOutputStream(statusFilePath.toFile());
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(out, TEXT_DIGEST_CHARSET);
             BufferedWriter writer = new BufferedWriter(outputStreamWriter)) {
            writer.write(Hex.encodeHex(sourceDigestBytes));
            writer.newLine();
            writer.write(Hex.encodeHex(targetDigestBytes));
            writer.newLine();
        }
    }

    private void generateJs(List<Path> soyFiles, SoyFileSet soyFileSet, SoyMsgBundle soyMsgBundle, Path outputPath)
            throws IOException {

        final List<String> compiledSources = soyFileSet.compileToJsSrc(
                firstNonNull(jsSrcOptions, new SoyJsSrcOptions()), soyMsgBundle);

        final Iterator<Path> soyFilePathIt = soyFiles.iterator();

        for (final String compiledSource : compiledSources) {

            final Path targetPath = outputPath.resolve(
                    Utils.changeSuffix(soyFilePathIt.next(), SOY_EXTENSION, JS_EXTENSION));

            Files.createDirectories(targetPath.getParent());

            try (FileOutputStream out = new FileOutputStream(targetPath.toFile());
                 OutputStreamWriter writer = new OutputStreamWriter(out, jsOutputCharsetName)) {
                writer.write(compiledSource);
            }
        }
    }

    private boolean isChanged(byte[] sourceDigestBytes)
            throws NoSuchAlgorithmException, IOException, DecoderException {

        if (!jsOutputDirectory.exists()) {
            return true;
        }

        final Path statusFilePath = getStatusFilePath();

        if (!Files.exists(statusFilePath)) {
            return true;
        }

        final byte[] targetDigestBytes = getGeneratedFilesDigest();

        try (FileInputStream in = new FileInputStream(statusFilePath.toFile());
             InputStreamReader inputStreamReader = new InputStreamReader(in);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            return !Arrays.equals(sourceDigestBytes, Hex.decodeHex(reader.readLine().toCharArray())) ||
                   !Arrays.equals(targetDigestBytes, Hex.decodeHex(reader.readLine().toCharArray()));
        }
    }

    private List<Path> getXliffFiles() throws IOException {
        if (translations.exists()) {
            return Utils.getFilesFromSubtree(translations.toPath(), XLIFF_EXTENSION);
        } else {
            getLog().info("Translations directory does not exist: " + translations);
            return Collections.emptyList();
        }
    }

    private Path getJavaSourceOutputPath() {
        final Path javaOutputPath = javaOutputDirectory.toPath();
        final Path javapackageSubpath = javaOutputPath.getFileSystem().getPath("", javaPackage.split("\\."));
        return javaOutputPath.resolve(javapackageSubpath);
    }

    private Path getStatusFilePath() throws NoSuchAlgorithmException {
        final MessageDigest statusDigest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        statusDigest.update(execution.getExecutionId().getBytes(TEXT_DIGEST_CHARSET));
        return markersDirectory.toPath().resolve(Hex.encodeHexString(statusDigest.digest()));
    }

    private byte[] getSourceDigestBytes(List<Path> soyFiles, List<Path> xliffFiles)
            throws NoSuchAlgorithmException, IOException {
        final MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
        md.update(getFilesDigest(sources.toPath(), soyFiles));
        md.update(getFilesDigest(translations.toPath(), xliffFiles));
        return md.digest();
    }

    private byte[] getGeneratedFilesDigest() throws IOException, NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
        md.update(getFilesDigest(jsOutputDirectory));
        md.update(getFilesDigest(javaOutputDirectory));
        return md.digest();
    }

    private static byte[] getFilesDigest(File directory) throws IOException, NoSuchAlgorithmException {
        final Path path = directory.toPath();
        return getFilesDigest(path, Utils.getFilesFromSubtree(path));
    }

    private static byte[] getFilesDigest(Path root, List<Path> files) throws IOException, NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
        Collections.sort(files);
        for (final Path soyFilePath : files) {
            updateDigest(root, md, soyFilePath);
        }
        return md.digest();
    }

    private static void updateDigest(Path root, MessageDigest sourceDigest, Path soyFilePath) throws IOException {
        sourceDigest.update(soyFilePath.toString().getBytes(TEXT_DIGEST_CHARSET));
        final Path soyFile = root.resolve(soyFilePath);
        sourceDigest.update(Longs.toByteArray(Files.size(soyFile)));
        sourceDigest.update(Longs.toByteArray(Files.getLastModifiedTime(soyFile).toMillis()));
    }
}
