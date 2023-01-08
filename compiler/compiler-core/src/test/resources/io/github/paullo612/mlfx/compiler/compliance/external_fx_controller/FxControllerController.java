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
package io.github.paullo612.mlfx.compiler.compliance.external_fx_controller;

import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

public class FxControllerController {

    public enum WindowState {
        CLOSED,
        OPENED
    }

    private final Property<WindowState> windowState = new SimpleObjectProperty<>(WindowState.CLOSED);

    public WindowState getWindowState() {
        return windowState.getValue();
    }

    public Property<WindowState> windowStateProperty() {
        return windowState;
    }

    public void setWindowState(WindowState value) {
        windowState.setValue(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FxControllerController)) {
            return false;
        }

        FxControllerController other = (FxControllerController) obj;

        return Objects.equals(windowState.getValue(), other.windowState.getValue());
    }

    private String dumpFields() {
        return "  windowState = " + windowState.getValue();
    }

    @Override
    public String toString() {
        return "FxControllerController {\n" + dumpFields() + '}';
    }
}