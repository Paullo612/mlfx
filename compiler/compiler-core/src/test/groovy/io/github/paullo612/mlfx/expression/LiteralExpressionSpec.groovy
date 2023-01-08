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

class LiteralExpressionSpec extends ExpressionSpec {

    def "Literal (#literalType): #literal == #expectedResult"() {
        given:
        def context = buildExpressionContext("""
package io.github.paullo612.mlfx.expression.test;

@Expression
@jakarta.inject.Singleton
abstract class TestClass {
        
    @Expression.Value("$literal")
    abstract $literalType compute();
}
""")
        def bean = getBean(context, 'io.github.paullo612.mlfx.expression.test.TestClass')

        when:
        def result = bean.compute()

        then:
        result == expectedResult

        where:
        literal   | literalType | expectedResult
        1         | 'byte'      | 1
        2         | 'short'     | 2
        3         | 'int'       | 3
        4         | 'long'      | 4
        5.0       | 'float'     | 5.0
        6.0       | 'double'    | 6.0
        1         | 'Byte'      | 1
        2         | 'Short'     | 2
        3         | 'Integer'   | 3
        4         | 'Long'      | 4
        5.0       | 'Float'     | 5.0
        6.0       | 'Double'    | 6.0
        true      | 'boolean'   | true
        "'false'" | 'Boolean'   | false
        null      | 'Object'    | null
        null      | 'String'    | null
        "'null'"  | 'String'    | 'null'
        "'true'"  | 'String'    | 'true'
        "'false'" | 'String'    | 'false'
        "'1'"     | 'String'    | '1'
        "'2.0'"   | 'String'    | '2.0'
    }
}
