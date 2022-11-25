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
package org.acme.commonutil;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class HttpUtilTest { 

    @Test
    public void testBasicAuthHeader() throws Exception {
        String header = HttpUtil.basicAuthHeader("username", "password");
        Assertions.assertEquals("Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=", header);
    } 

    @Test
    public void testBasicAuthHeaderValue() throws Exception {
        String authvalue = HttpUtil.basicAuthHeaderValue("username", "password");
        Assertions.assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", authvalue);
    }
} 
