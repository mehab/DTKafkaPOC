/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.acme.model;

import com.github.packageurl.PackageURL;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RepositoryTypeTest {

    @Test
    public void testEnums() {
        Assertions.assertEquals("MAVEN", RepositoryType.MAVEN.name());
        Assertions.assertEquals("NPM", RepositoryType.NPM.name());
        Assertions.assertEquals("GEM", RepositoryType.GEM.name());
        Assertions.assertEquals("PYPI", RepositoryType.PYPI.name());
        Assertions.assertEquals("NUGET", RepositoryType.NUGET.name());
        Assertions.assertEquals("HEX", RepositoryType.HEX.name());
        Assertions.assertEquals("UNSUPPORTED", RepositoryType.UNSUPPORTED.name());
    }

    @Test
    public void testResolveMaven() throws Exception {
        PackageURL purl = new PackageURL("pkg:maven/groupId/artifactId@1.0.0");
        Assertions.assertEquals(RepositoryType.MAVEN, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolveNpm() throws Exception {
        PackageURL purl = new PackageURL("pkg:npm/artifact@1.0.0");
        Assertions.assertEquals(RepositoryType.NPM, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolveGem() throws Exception {
        PackageURL purl = new PackageURL("pkg:gem/artifact@1.0.0");
        Assertions.assertEquals(RepositoryType.GEM, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolvePypi() throws Exception {
        PackageURL purl = new PackageURL("pkg:pypi/artifact@1.0.0");
        Assertions.assertEquals(RepositoryType.PYPI, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolveNuget() throws Exception {
        PackageURL purl = new PackageURL("pkg:nuget/artifact@1.0.0");
        Assertions.assertEquals(RepositoryType.NUGET, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolveHex() throws Exception {
        PackageURL purl = new PackageURL("pkg:hex/phoenix@1.14.10");
        Assertions.assertEquals(RepositoryType.HEX, RepositoryType.resolve(purl));
    }

    @Test
    public void testResolveUnsupported() throws Exception {
        PackageURL purl = new PackageURL("pkg:generic/artifact@1.0.0");
        Assertions.assertEquals(RepositoryType.UNSUPPORTED, RepositoryType.resolve(purl));
    }
} 
