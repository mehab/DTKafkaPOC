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
package org.acme.resources.v1.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.acme.model.Cwe;
import org.acme.parser.common.resolver.CweResolver;
import java.io.IOException;
import java.util.List;

/**
 * Custom serializer which takes in a List of CWE IDs (Integer) serializes them into an JSON array of Cwe objects.
 * @since 4.5.0
 */
public class CweSerializer extends JsonSerializer<List<Integer>> {

    @Override
    public void serialize(List<Integer> cweIds, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {

        jsonGenerator.writeStartArray();
        for (final Integer cweId: cweIds) {
            final Cwe cwe = CweResolver.getInstance().lookup(cweId);
            if (cwe != null) {
                jsonGenerator.writeObject(cwe);
            }
        }
        jsonGenerator.writeEndArray();
    }
}
