/**
 * AET
 *
 * Copyright (C) 2013 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.cognifide.aet.vs.metadata;

import com.cognifide.aet.communication.api.metadata.Comparator;
import com.cognifide.aet.communication.api.metadata.Step;
import com.cognifide.aet.communication.api.metadata.Suite;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import org.bson.Document;

class DocumentConverter {

  private static final Gson GSON_FOR_MONGO_JSON;

  static final Map<Class, JsonDeserializer> DESERIALIZERS = new HashMap<>();

  static {
    DESERIALIZERS.put(Suite.Timestamp.class, new TimestampDeserializer());
    DESERIALIZERS.put(Step.class, new StepDeserializer());
    DESERIALIZERS.put(Comparator.class, new ComparatorDeserializer());

    GsonBuilder builder = new GsonBuilder();
    DESERIALIZERS.forEach(builder::registerTypeAdapter);

    GSON_FOR_MONGO_JSON = builder.create();
  }

  private static final Type SUITE_TYPE = new TypeToken<Suite>() {
  }.getType();

  private final Document document;

  DocumentConverter(Document document) {
    this.document = document;
  }

  Suite toSuite() {
    Suite suite = null;
    if (document != null) {
      String jsonRepresentation = document.toJson();
      suite = GSON_FOR_MONGO_JSON.fromJson(jsonRepresentation, SUITE_TYPE);
    }
    return suite;
  }
}
