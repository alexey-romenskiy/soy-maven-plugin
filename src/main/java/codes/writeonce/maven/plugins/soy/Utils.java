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

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

final class Utils {

    public static List<Path> getFilesFromSubtree(Path directory) throws IOException {
        return getFilesFromSubtree(directory, path -> Files.isRegularFile(path));
    }

    public static List<Path> getFilesFromSubtree(Path directory, String nameSuffix) throws IOException {
        return getFilesFromSubtree(directory,
                path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(nameSuffix));
    }

    public static List<Path> getFilesFromSubtree(Path directory, Predicate<? super Path> predicate) throws IOException {

        if (!Files.isDirectory(directory)) {
            throw new IOException("Not a directory: " + directory);
        }

        return Files.walk(directory, FileVisitOption.FOLLOW_LINKS)
                    .filter(predicate)
                    .map(directory::relativize)
                    .collect(Collectors.toList());
    }

    public static Path changeSuffix(Path path, String fromNameSuffix, String toNameSuffix) {
        final String fileName = path.getFileName().toString();
        if (!fileName.endsWith(fromNameSuffix)) {
            throw new IllegalArgumentException(
                    "Path file name \"" + path + "\" does not end with suffix \"" + fromNameSuffix + "\"");
        }
        return path.resolveSibling(fileName.subSequence(0, fileName.length() - fromNameSuffix.length()) + toNameSuffix);
    }

    public static Path removeSuffix(Path path, String nameSuffix) {
        final String fileName = path.getFileName().toString();
        if (!fileName.endsWith(nameSuffix)) {
            throw new IllegalArgumentException(
                    "Path file name \"" + path + "\" does not end with suffix \"" + nameSuffix + "\"");
        }
        return path.resolveSibling(fileName.substring(0, fileName.length() - nameSuffix.length()));
    }

    private Utils() {
        // empty
    }
}
