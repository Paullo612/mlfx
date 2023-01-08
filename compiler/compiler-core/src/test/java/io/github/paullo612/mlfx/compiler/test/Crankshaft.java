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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

public class Crankshaft {

    private final ObjectProperty<Flywheel> flywheel = new SimpleObjectProperty<>(new Flywheel());

    private final ObservableList<Crankpin> crankpins = FXCollections.observableArrayList();

    private final StringProperty manufacturer = new SimpleStringProperty();

    public ObservableList<Crankpin> getCrankpins() {
        return crankpins;
    }

    public Flywheel getFlywheel() {
        return flywheel.get();
    }

    public ReadOnlyObjectProperty<Flywheel> flywheelProperty() {
        return flywheel;
    }

    public String getManufacturer() {
        return manufacturer.get();
    }

    public void setManufacturer(String value) {
        manufacturer.set(value);
    }

    public StringProperty manufacturerProperty() {
        return manufacturer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Crankshaft other = (Crankshaft) o;

        return Objects.equals(flywheel.get(), other.flywheel.get())
                && crankpins.equals(other.crankpins)
                && Objects.equals(manufacturer.get(), other.manufacturer.get());
    }

    @Override
    public String toString() {
        return "Crankshaft { flywheel = " + flywheel + ", crankpins = " + crankpins + ", manufacturer = "
                + manufacturer.get() + "}";
    }
}
