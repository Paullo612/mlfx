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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

@DefaultProperty("teethCount")
public class Flywheel {

    private final IntegerProperty teethCount = new SimpleIntegerProperty(0);

    public IntegerProperty teethCountProperty() {
        return teethCount;
    }

    public int getTeethCount() {
        return teethCount.get();
    }

    public void setTeethCount(int value) {
        teethCount.set(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Flywheel other = (Flywheel) o;

        return teethCount.get() == other.teethCount.get();
    }

    @Override
    public String toString() {
        return "Flywheel { teethCount = " + teethCount.get() + "}";
    }
}
