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

import com.google.template.soy.msgs.SoyMsgBundle;
import com.google.template.soy.msgs.SoyMsgBundleHandler;
import com.google.template.soy.xliffmsgplugin.XliffMsgPlugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Files;

@Mojo(name = "extract-messages")
public class ExtractMessagesMojo extends AbstractSoyMojo {

    private static final String DEFAULT_XLIFF_OUTPUT_PATH = "src/main/i18n/";

    @Parameter(required = true, property = "soy.messages.sourceLocale")
    private String sourceLocale;

    @Parameter(required = true, property = "soy.messages.targetLocale")
    private String targetLocale;

    @Parameter
    private File outputFile;

    @Parameter(required = true, defaultValue = "false", property = "soy.messages.overwrite")
    private boolean overwrite;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            process();
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            getLog().error(e.getMessage());
            throw new MojoExecutionException("Error extracting messages: " + e.getMessage(), e);
        }
    }

    @Override
    protected void process() throws Exception {

        super.process();

        if (outputFile == null) {
            outputFile = new File(DEFAULT_XLIFF_OUTPUT_PATH + targetLocale + XLIFF_EXTENSION);
        }

        outputFile = getSafePath(outputFile);

        if (Files.exists(outputFile.toPath())) {
            if (overwrite) {
                getLog().info("Overwriting the existing file: " + outputFile);
            } else {
                getLog().error("File already exists: " + outputFile);
                throw new MojoExecutionException("File already exists: " + outputFile);
            }
        } else {
            getLog().info("Generating the messages file: " + outputFile);
        }

        final SoyMsgBundleHandler.OutputFileOptions options = new SoyMsgBundleHandler.OutputFileOptions();
        options.setSourceLocaleString(sourceLocale);

        if (targetLocale.length() > 0) {
            options.setTargetLocaleString(targetLocale);
        }

        final SoyMsgBundleHandler msgBundleHandler = new SoyMsgBundleHandler(new XliffMsgPlugin());
        final SoyMsgBundle soyMsgBundle = getSoyFileSet(getSoyFiles()).extractMsgs();
        msgBundleHandler.writeToExtractedMsgsFile(soyMsgBundle, options, outputFile);
    }
}
