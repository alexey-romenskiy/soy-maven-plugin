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

import com.google.common.collect.Maps;
import com.google.template.soy.SoyFileSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public abstract class AbstractSoyMojo extends AbstractMojo {

    protected static final String SOY_EXTENSION = ".soy";
    protected static final String XLIFF_EXTENSION = ".xlf";

    @Parameter(required = true, defaultValue = "${basedir}/src/main/soy")
    protected File sources;

    @Parameter
    private Properties compileTimeGlobals;

    @Parameter(required = true, readonly = true, defaultValue = "${project}")
    protected MavenProject project;

    protected void process() throws Exception {
        sources = getSafePath(sources);
    }

    protected List<Path> getSoyFiles() throws IOException {
        if (sources.exists()) {
            return Utils.getFilesFromSubtree(sources.toPath(), SOY_EXTENSION);
        } else {
            getLog().warn("Source directory does not exist: " + sources);
            return Collections.emptyList();
        }
    }

    protected File getSafePath(File file) {
        return project.getBasedir().toPath().resolve(file.toPath()).toFile();
    }

    protected SoyFileSet getSoyFileSet(List<Path> soyFiles) {

        final SoyFileSet.Builder soyFileSetBuilder = SoyFileSet.builder();

        if (compileTimeGlobals != null) {
            soyFileSetBuilder.setCompileTimeGlobals(Maps.fromProperties(compileTimeGlobals));
        }

        for (final Path soyFilePath : soyFiles) {
            soyFileSetBuilder.add(sources.toPath().resolve(soyFilePath).toFile());
        }

        return soyFileSetBuilder.build();
    }
}
