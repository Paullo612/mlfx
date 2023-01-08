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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

@IDProperty("id")
public class CoercibleIdentifiable {

    // NB: SimpleIntegerProperty is not applicable here, as it will return 0 as (already set) value, and property will
    //  not be set by FXMLLoader.
    private final ObjectProperty<Integer> id = new SimpleObjectProperty<>();

    public Property<Integer> idProperty() {
        return id;
    }

    public Integer getId() {
        return id.get();
    }

    public void setId(Integer value) {
        id.set(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CoercibleIdentifiable that = (CoercibleIdentifiable) o;
        return Objects.equals(id.get(), that.id.get());
    }

    @Override
    public String toString() {
        return "Identifiable{\n  id=" + id.get() + '}';
    }
}
