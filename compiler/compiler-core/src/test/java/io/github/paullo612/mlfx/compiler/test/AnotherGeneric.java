/*
 * Copyright 2025 Paullo612
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

public class AnotherGeneric<T extends String> {

    private final ObjectProperty<T> foo = new SimpleObjectProperty<>();
    private final ObjectProperty<Generic<T>> bar = new SimpleObjectProperty<>(new Generic<>());

    public ObjectProperty<T> fooProperty() {
        return foo;
    }

    public T getFoo() {
        return foo.get();
    }

    public void setFoo(T value) {
        foo.set(value);
    }

    public ObjectProperty<Generic<T>> barProperty() {
        return bar;
    }

    public Generic<T> getBar() {
        return bar.get();
    }

    public void setBar(Generic<T> value) {
        bar.set(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AnotherGeneric<?> other = (AnotherGeneric<?>) o;
        return Objects.equals(foo.get(), other.foo.get())
                && Objects.equals(bar.get(), other.bar.get());
    }

    @Override
    public String toString() {
        return "AnotherGeneric { foo = " + foo.get() + ", bar = " + bar.get() + "}";
    }
}
