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

public enum JavaClassNameSource {

    /**
     * AaaBbb.soy or aaa_bbb.soy --> AaaBbbSoyInfo.
     */
    SOY_FILE_NAME("filename"),

    /**
     * boo.foo.aaaBbb --> AaaBbbSoyInfo.
     */
    SOY_NAMESPACE_LAST_PART("namespace"),

    /**
     * File1SoyInfo, File2SoyInfo, etc.
     */
    GENERIC("generic");

    private final String value;

    JavaClassNameSource(String value) {
        // TODO:
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
