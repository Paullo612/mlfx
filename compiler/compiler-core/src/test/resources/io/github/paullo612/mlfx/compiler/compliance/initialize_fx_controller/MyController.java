/*
 * Copyright 2023 Paullo612
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.paullo612.mlfx.compiler.compliance.initialize_fx_controller;

import javafx.fxml.FXML;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class MyController {

    public URL location;

    @FXML
    private ResourceBundle resources;

    private boolean initialized;

    @FXML
    private void initialize() {
        initialized = true;
    }

    private boolean compareLocation(MyController other) {
        if (location == other.location) {
            return true;
        }

        if ((location != null && other.location == null) || (location == null && other.location != null)) {
            return false;
        }

        try {
            return new URL(location, "./").equals(new URL(other.location, "./"));
        } catch (MalformedURLException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MyController)) {
            return false;
        }

        MyController other = (MyController) obj;

        return compareLocation(other) && Objects.equals(resources, other.resources) && initialized == other.initialized;
    }

    private String dumpFields() {
        return "  location = " + location + ",\n  resources = " + resources + ",\n  initialized = " + initialized;
    }

    @Override
    public String toString() {
        return "MyController {\n" + dumpFields() + '}';
    }
}