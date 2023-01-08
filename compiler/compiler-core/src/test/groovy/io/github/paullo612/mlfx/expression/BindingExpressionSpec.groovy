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

import io.github.paullo612.mlfx.compiler.BindingExpressionRendererImpl
import io.github.paullo612.mlfx.expression.test.PropertiesImpl
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue

class BindingExpressionSpec extends ExpressionSpec {

    def "Binding expression observable change"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import io.github.paullo612.mlfx.expression.test.PropertiesImpl;import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    final Properties a = new PropertiesImpl();
    final Properties b = new PropertiesImpl();
        
    @Expression.Value("a.property2 + b.property5")
    abstract ObservableValue<Integer> compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')
        bean.a.setProperty2("$property2Default")
        bean.b.setProperty5(property5Default)
        ObservableValue<Integer> result = bean.compute()
        ChangeListener<Integer> listener = Mock(ChangeListener)
        result.addListener(listener)

        expect:
        result["${BindingExpressionRendererImpl.ARG_CAPTURE_NAME}0"] == bean.a
        result["${BindingExpressionRendererImpl.ARG_CAPTURE_NAME}1"] == bean.b

        when:
        def value = result.getValue()

        then:
        value == property2Default + property5Default
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == "$property2Default"
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property5Default

        when:
        bean.a.setProperty2("$property2New")

        then:
        1 * listener.changed(result, property2Default + property5Default, property2New + property5Default)
        result.getValue() == property2New + property5Default
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == "$property2New"
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property5Default

        when:
        bean.b.setProperty5(property5New)

        then:
        1 * listener.changed(result, property2New + property5Default, property2New + property5New)
        result.getValue() == property2New + property5New
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == "$property2New"
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property5New

        where:
        property2Default | property2New | property5Default | property5New
        8                | 9            | 65               | 8
    }

    def "Binding expression && short circuit"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    Properties a;
        
    @Expression.Value("(a.property5 == $property5New) && a.property2.contains('$property2New') && a.property4 == 42")
    abstract ObservableValue<Boolean> compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')
        PropertiesImpl properties = Spy(PropertiesImpl)
        properties.setProperty2(property2Default)
        properties.property4Property().setValue(42)
        properties.setProperty5(property5Default)

        bean.a = properties

        ObservableValue<Boolean> result = bean.compute()
        ChangeListener<Boolean> listener = Mock(ChangeListener)
        result.addListener(listener)

        expect:
        result["${BindingExpressionRendererImpl.ARG_CAPTURE_NAME}0"] == properties

        when: 'request expression value'
        def value = result.getValue()

        then: 'left part of && expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5Default

        and: 'right part is not'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == null
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0

        when:
        properties.setProperty5(property5New)
        value = result.getValue()

        then: 'left part of && expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5New
        1 * properties.getProperty5()

        and: 'right part is also executed'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property2Default
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0
        1 * properties.getProperty2()
        0 * properties.getProperty4()

        when:
        properties.setProperty5(property5Default)
        value = result.getValue()

        then: 'left part of && expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5Default
        1 * properties.getProperty5()

        and: 'right part is not, cached values should be cleared'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == null
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0
        0 * properties.getProperty2()
        0 * properties.getProperty4()

        when:
        properties.setProperty5(property5New)
        value = result.getValue()

        then: 'left part of && expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5New
        1 * properties.getProperty5()

        and: 'right part is also executed'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property2Default
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0
        1 * properties.getProperty2()
        0 * properties.getProperty4()

        where:
        property2Default | property2New | property5Default | property5New
        'bar'            | 'foo'        | 65               | 43
    }

    def "Binding expression || short circuit"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

import javafx.beans.value.ObservableValue;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {

    Properties a;
        
    @Expression.Value("((a.property5 == $property5New) || a.property2.contains('$property2New')) && a.property4 == 42")
    abstract ObservableValue<Boolean> compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')
        PropertiesImpl properties = Spy(PropertiesImpl)
        properties.setProperty2(property2Default)
        properties.property4Property().setValue(42)
        properties.setProperty5(property5Default)

        bean.a = properties

        ObservableValue<Boolean> result = bean.compute()
        ChangeListener<Boolean> listener = Mock(ChangeListener)
        result.addListener(listener)

        expect:
        result["${BindingExpressionRendererImpl.ARG_CAPTURE_NAME}0"] == properties

        when: 'request expression value'
        def value = result.getValue()

        then: 'left part of || expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5Default

        and: 'right part is also executed'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property2Default
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0

        when:
        properties.setProperty5(property5New)
        value = result.getValue()

        then: 'left part of || expression is executed'
        value == true
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5New
        1 * properties.getProperty5()

        and: 'right part is not, cached values should be cleared'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == null
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 42
        0 * properties.getProperty2()
        1 * properties.getProperty4()

        when:
        properties.setProperty5(property5Default)
        value = result.getValue()

        then: 'left part of || expression is executed'
        value == false
        result["${BindingExpressionRendererImpl.STORE_NAME}0"] == property5Default
        1 * properties.getProperty5()

        and: 'right part is also executed'
        result["${BindingExpressionRendererImpl.STORE_NAME}1"] == property2Default
        result["${BindingExpressionRendererImpl.STORE_NAME}4"] == 0
        1 * properties.getProperty2()
        0 * properties.getProperty4()

        where:
        property2Default | property2New | property5Default | property5New
        'bar'            | 'foo'        | 65               | 43
    }
}
