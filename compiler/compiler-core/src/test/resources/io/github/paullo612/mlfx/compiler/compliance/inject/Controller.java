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
package io.github.paullo612.mlfx.compiler.compliance.inject;

import io.github.paullo612.mlfx.compiler.test.Wheel;
import javafx.fxml.FXML;

import java.util.Objects;

public class Controller {

    @FXML
    private Wheel wheelOne;

    public Wheel wheelTwo;

    @FXML
    Wheel wheelThree;

    @FXML
    protected Wheel wheelFour;

    @FXML
    private Wheel wheelFive;

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Controller)) {
            return false;
        }

        Controller other = (Controller) obj;

        return Objects.equals(wheelOne, other.wheelOne)
                && Objects.equals(wheelTwo, other.wheelTwo)
                && Objects.equals(wheelThree, other.wheelThree)
                && Objects.equals(wheelFour, other.wheelFour)
                && Objects.equals(wheelFive, other.wheelFive);
    }

    private String dumpFields() {
        return "  wheelOne = " + wheelOne + ",\n  wheelTwo = " + wheelTwo + ",\n  wheelThree = " + wheelThree
                + ",\n  wheelFour = " + wheelFour + ",\n  wheelFive = " + wheelFive;
    }

    @Override
    public String toString() {
        return "InjectController {\n" + dumpFields() + '}';
    }
}