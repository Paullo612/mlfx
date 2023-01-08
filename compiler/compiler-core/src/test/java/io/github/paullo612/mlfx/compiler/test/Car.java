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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@DefaultProperty("engine")
public class Car {

    public static void setEngineLocation(Engine engine, EngineLocation location) {
        engine.engineLocation = location;
    }

    // NB: We're inferring static property type from getter, so, getter is mandatory here.
    public static EngineLocation getEngineLocation(Engine engine) {
        return engine.engineLocation;
    }

    private final Property<String> model = new SimpleStringProperty();

    private final ObjectProperty<Engine> engine = new SimpleObjectProperty<>();

    private final Engine anotherEngine = new Engine();

    private final List<String> bodyColors = new ArrayList<>();

    private final ObservableList<Wheel> wheels = FXCollections.observableArrayList();

    private final ObjectProperty<EventHandler<ActionEvent>> onDrive = new SimpleObjectProperty<>(this, "onDrive");

    public String getModel() {
        return model.getValue();
    }

    public void setModel(String model) {
        this.model.setValue(model);
    }

    public Property<String> modelProperty() {
        return model;
    }

    public Engine getEngine() {
        return engine.get();
    }

    public void setEngine(Engine engine) {
        this.engine.set(engine);
    }

    public Property<Engine> engineProperty() {
        return engine;
    }

    public Engine getAnotherEngine() {
        return anotherEngine;
    }

    public List<String> getBodyColors() {
        return bodyColors;
    }

    public ObservableList<Wheel> getWheels() {
        return wheels;
    }

    public void setOnDrive(EventHandler<ActionEvent> value) {
        onDriveProperty().set(value);
    }

    public EventHandler<ActionEvent> getOnDrive() {
        return onDriveProperty().get();
    }

    public ObjectProperty<EventHandler<ActionEvent>> onDriveProperty() {
        return onDrive;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Car)) {
            return false;
        }

        Car other = (Car) obj;

        return Objects.equals(model.getValue(), other.model.getValue())
                && Objects.equals(engine.get(), other.engine.get())
                && Objects.equals(anotherEngine, other.anotherEngine)
                && Objects.equals(bodyColors, other.bodyColors)
                && Objects.equals(wheels, other.wheels);
    }

    protected String dumpFields() {
        String engine;
        Engine engineValue = this.engine.get();

        engine = engineValue != null
                ? String.join("\n  ", engineValue.toString().split("\n"))
                : "null";

        String anotherEngine = String.join("\n  ", this.anotherEngine.toString().split("\n"));

        return "  model = " + model.getValue() +
                ",\n  engine = " +  engine +
                ",\n  anotherEngine = " +  anotherEngine +
                ",\n  bodyColors = " + bodyColors +
                ",\n  wheels = " + wheels;
    }

    @Override
    public String toString() {
        return "Car {\n" + dumpFields() + '}';
    }
}
