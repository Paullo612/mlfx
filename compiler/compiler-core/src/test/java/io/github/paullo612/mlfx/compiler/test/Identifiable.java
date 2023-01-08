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
package io.github.paullo612.mlfx.compiler.test;

import com.sun.javafx.beans.IDProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;

import java.util.Objects;

@IDProperty("id")
public class Identifiable {

    private final Property<String> id = new SimpleStringProperty();

    public Property<String> idProperty() {
        return id;
    }

    public String getId() {
        return id.getValue();
    }

    public void setId(String value) {
        id.setValue(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Identifiable that = (Identifiable) o;
        return Objects.equals(id.getValue(), that.id.getValue());
    }

    @Override
    public String toString() {
        return "Identifiable{\n  id=" + id.getValue() + '}';
    }
}
