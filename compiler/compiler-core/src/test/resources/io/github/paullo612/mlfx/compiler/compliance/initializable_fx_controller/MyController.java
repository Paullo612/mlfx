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
package io.github.paullo612.mlfx.compiler.compliance.initializable_fx_controller;

import javafx.fxml.Initializable;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class MyController implements Initializable {

    private URL location;
    private ResourceBundle resources;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        assert this.location == null;
        assert this.resources == null;

        this.location = location;
        this.resources = resources;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof MyController)) {
            return false;
        }

        MyController other = (MyController) obj;

        return Objects.equals(location, other.location)
                && Objects.equals(resources, other.resources);
    }

    private String dumpFields() {
        return "  location = " + location + ",\n  resources = " + resources;
    }

    @Override
    public String toString() {
        return "MyController {\n" + dumpFields() + '}';
    }
}