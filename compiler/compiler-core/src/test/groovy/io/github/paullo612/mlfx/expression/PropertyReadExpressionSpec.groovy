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
package io.github.paullo612.mlfx.expression


import io.github.paullo612.mlfx.expression.test.Properties
import io.github.paullo612.mlfx.expression.test.PropertiesImpl

class PropertyReadExpressionSpec extends ExpressionSpec {

    def "Property read (#aType#propertyRef => #ret): #propertyRead == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("$propertyRead")
    abstract $ret compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        aType            | ret      | a                      | property    | expectedResult
        'Properties'     | 'String' | 'new PropertiesImpl()' | 'property1' | PropertiesImpl.PROPERTY1_VALUE
        'PropertiesImpl' | 'String' | 'new PropertiesImpl()' | 'property2' | PropertiesImpl.PROPERTY2_VALUE
        'PropertiesImpl' | 'int'    | 'new PropertiesImpl()' | 'property3' | PropertiesImpl.PROPERTY3_VALUE
        'Properties'     | 'int'    | 'new PropertiesImpl()' | 'property4' | PropertiesImpl.PROPERTY4_VALUE

        propertyRef = "#$property"
        propertyRead = "a.$property"
    }

    def "Observable property read (#aType#propertyRef => #ret): #propertyRead == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("$propertyRead")
    abstract ObservableValue<$ret> compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute().getValue()

        then:
        result == expectedResult

        where:
        aType            | ret       | a                      | property    | expectedResult
        'Properties'     | 'String'  | 'new PropertiesImpl()' | 'property2' | PropertiesImpl.PROPERTY2_VALUE
        'PropertiesImpl' | 'Integer' | 'new PropertiesImpl()' | 'property4' | PropertiesImpl.PROPERTY4_VALUE

        propertyRef = "#$property"
        propertyRead = "a.$property"
    }

    def "Multiple properties read"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final PropertiesImpl a = new PropertiesImpl();
    final PropertiesImpl b = new PropertiesImpl();
        
    @Expression.Value("a.property3 + b.property4")
    abstract ObservableValue<Integer> compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute().getValue()

        then:
        result == PropertiesImpl.PROPERTY3_VALUE + PropertiesImpl.PROPERTY4_VALUE
    }

    def "Property read NC (#aType#propertyRef => #ret): #propertyRead fails with '#expectedResult'"() {
        when:
        buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final $aType a = $a;
        
    @Expression.Value("$propertyRead")
    abstract $ret compute();
}
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedResult)

        where:
        aType            | ret                        | a                      | property
        'PropertiesImpl' | 'String'                   | 'new PropertiesImpl()' | 'foo'
        'Properties'     | 'ObservableValue<String>'  | 'new PropertiesImpl()' | 'foo'
        'PropertiesImpl' | 'String'                   | 'new PropertiesImpl()' | 'writeOnly'
        ________________________________________________________________________________________________________
        expectedResult                                                                                       | _
        String.format(PropertyReadContinuation.NO_SUCH_PROPERTY_ERROR_FORMAT, property, PropertiesImpl.name) | _
        String.format(PropertyReadContinuation.NO_SUCH_PROPERTY_ERROR_FORMAT, property, Properties.name) | _
        String.format(PropertyReadContinuation.NO_SUCH_PROPERTY_ERROR_FORMAT, property, PropertiesImpl.name) | _

        propertyRef = "#$property"
        propertyRead = "a.$property"
    }
}
