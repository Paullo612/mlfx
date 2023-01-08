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
package io.github.paullo612.mlfx.compiler.compliance.handlers;

import io.github.paullo612.mlfx.compiler.test.Car;
import io.github.paullo612.mlfx.compiler.test.Wheel;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import java.util.Objects;

public class Controller {

    @FXML
    private Car car;

    private boolean wheelsChangedCalled;
    private boolean modelChangedCalled;
    private boolean driveCalled;

    public void initialize() {
        car.getWheels().add(new Wheel());
        car.setModel("Ford");
        car.getOnDrive().handle(new ActionEvent());
    }

    public void onWheelsChanged(ListChangeListener.Change<Wheel> change) {
        wheelsChangedCalled = true;
    }

    @FXML
    void onModelChanged(ObservableValue<String> observableValue, String oldValue, String newValue) {
        modelChangedCalled = true;
    }

    @FXML
    private void drive(ActionEvent event) {
        event.consume();
        driveCalled = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Controller)) {
            return false;
        }

        Controller other = (Controller) obj;

        return Objects.equals(car, other.car)
                && wheelsChangedCalled == other.wheelsChangedCalled
                && modelChangedCalled == other.modelChangedCalled
                && driveCalled == other.driveCalled;
    }

    private String dumpFields() {
        return "  car = " + car + ",\n  wheelsChangedCalled = " + wheelsChangedCalled + ",\n  modelChangedCalled = "
                + modelChangedCalled + ",\n  driveCalled = " + driveCalled;
    }

    @Override
    public String toString() {
        return "HandlersController {\n" + dumpFields() + '}';
    }
}