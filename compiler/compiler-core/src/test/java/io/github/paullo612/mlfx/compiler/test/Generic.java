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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

public class Generic<T> {

    private final ObjectProperty<T> generic = new SimpleObjectProperty<>();

    public ObjectProperty<T> genericProperty() {
        return generic;
    }

    public T getGeneric() {
        return generic.get();
    }

    public void setGeneric(T value) {
        generic.set(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Generic<?> other = (Generic<?>) o;
        return Objects.equals(generic.get(), other.generic.get());
    }

    @Override
    public String toString() {
        return "Generic { generic = " + generic.get() + "}";
    }
}
