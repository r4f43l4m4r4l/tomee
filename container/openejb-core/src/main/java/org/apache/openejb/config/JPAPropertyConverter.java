/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
    * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.openejb.config;

import org.apache.openejb.loader.SystemInstance;

import java.util.Properties;

/**
 * @author rmannibucau
 */
public final class JPAPropertyConverter {
    private JPAPropertyConverter() {
        // no-op
    }

    public static class Pair {
        private String key;
        private String value;

        public Pair(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        @Override public String toString() {
            return key + '=' + value;
        }
    }

    // TODO: manage more properties
    public static Pair toOpenJPAValue(String key, String value, Properties properties) {
        if (!Boolean.parseBoolean(SystemInstance.get().getProperty("openejb.convert-jpa-properties", "false"))) {
            return null;
        }

        if (key.startsWith("eclipselink.ddl-generation") && !properties.containsKey("openjpa.jdbc.SchemaFactory")) {
            if ("create-tables".equals(value)) {
                return new Pair("openjpa.jdbc.SynchronizeMappings", "buildSchema(ForeignKeys=true)");
            } else if ("drop-and-create-tables".equals("value")) {
                return new Pair("openjpa.jdbc.SynchronizeMappings", "buildSchema(SchemaAction='add,deleteTableContents')");
            }
        }
        return null;
    }
}
