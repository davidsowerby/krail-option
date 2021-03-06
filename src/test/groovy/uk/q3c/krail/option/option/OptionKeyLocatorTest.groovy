/*
 *
 *  * Copyright (c) 2016. David Sowerby
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *
 */

package uk.q3c.krail.option.option

import com.google.common.collect.ImmutableList
import org.apache.commons.collections15.ListUtils
import spock.lang.Specification
import uk.q3c.krail.option.Option
import uk.q3c.krail.option.OptionKey
import uk.q3c.krail.option.persist.cache.AContext
import uk.q3c.util.data.collection.AnnotationList
import uk.q3c.util.data.collection.DataList

import static org.mockito.Mockito.*

/**
 *
 * Created by David Sowerby on 07/08/15.
 */
class OptionKeyLocatorTest extends Specification {

    OptionKeyLocator locator
    Option option = mock(Option)

    def setup() {
        locator = new OptionKeyLocator()
    }

    def "option key data types"() {
        given:
        def expected = ImmutableList.of(Integer.class, String.class, Locale.class, Boolean.class, Enum.class, AnnotationList.class, DataList.class, Long.class)


        when:

        def actual = locator.contextKeyTypes()
        def diff = ListUtils.subtract(new ArrayList(actual), expected)


        then:

//        actual.containsAll(expected) not all key types are used
        diff.isEmpty() // trap any new ones which get added but may not be in DataConverter

    }

    def "context key map"() {

        when:
        Map<OptionKey, Class<?>> map = locator.contextKeyMap(new AContext(option))

        then:
        map.size() == 2
        map.containsKey(AContext.key1)
        map.containsKey(AContext.key2)
    }
}
