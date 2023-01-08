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

import javafx.beans.DefaultProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;

@DefaultProperty("manufacturer")
public class Engine {

    public static Engine createV8Engine() {
        Engine engine = new Engine();
        engine.setEngineType(EngineType.V8);

        return engine;
    }

    private EngineType engineType;

    EngineLocation engineLocation;

    private final StringProperty manufacturer = new SimpleStringProperty();

    private final Crankshaft crankshaft = new Crankshaft();

    public EngineType getEngineType() {
        return engineType;
    }

    public void setEngineType(EngineType engineType) {
        this.engineType = engineType;
    }

    public String getManufacturer() {
        return manufacturer.get();
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer.set(manufacturer);
    }

    public StringProperty manufacturerProperty() {
        return manufacturer;
    }

    public Crankshaft getCrankshaft() {
        return crankshaft;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Engine)) {
            return false;
        }

        Engine other = (Engine) obj;

        return Objects.equals(engineType, other.engineType)
                && Objects.equals(engineLocation, other.engineLocation)
                && Objects.equals(manufacturer.get(), other.manufacturer.get())
                && crankshaft.equals(other.crankshaft);
    }

    @Override
    public String toString() {
        return "Engine {\n" +
                "  engineType = " + engineType +
                ",\n  engineLocation = " + engineLocation +
                ",\n  manufacturer = " + manufacturer.get() +
                ",\n  crankshaft = " + crankshaft +
                "\n}";
    }
}
