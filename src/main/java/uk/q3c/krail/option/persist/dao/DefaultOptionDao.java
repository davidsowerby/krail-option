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

package uk.q3c.krail.option.persist.dao;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import uk.q3c.krail.option.Option;
import uk.q3c.krail.option.OptionException;
import uk.q3c.krail.option.OptionKeyException;
import uk.q3c.krail.option.RankOption;
import uk.q3c.krail.option.persist.OptionCache;
import uk.q3c.krail.option.persist.OptionCacheKey;
import uk.q3c.krail.option.persist.OptionDao;
import uk.q3c.krail.option.persist.OptionDaoDelegate;
import uk.q3c.krail.option.persist.OptionSource;
import uk.q3c.krail.option.persist.cache.DefaultOptionCacheLoader;
import uk.q3c.krail.persist.inmemory.store.DefaultInMemoryOptionStore;
import uk.q3c.util.data.DataConverter;
import uk.q3c.util.data.collection.DataList;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Data Access Object for {@link DefaultInMemoryOptionStore}
 * <br>
 * <b>NOTE:</b> All values to and from {@link Option} are natively typed.  All values to and from {@link OptionCache}, {@link DefaultOptionCacheLoader} and
 * {@link OptionDao} are wrapped in Optional.
 * <br>
 * Created by David Sowerby on 20/02/15.
 */
public class DefaultOptionDao implements OptionDao {

    private DataConverter dataConverter;
    private OptionDaoDelegate delegate;

    @Inject
    public DefaultOptionDao(DataConverter dataConverter, OptionSource delegateSource) {
        this.dataConverter = dataConverter;
        this.delegate = delegateSource.getActiveDao();
    }

    /**
     * Write the key value pair
     *
     * @param cacheKey specifies the hierarchy, rank and OptionKey to write to
     * @param value    the value to write
     * @param <V>      the value type
     */
    @Override
    public <V> void write(OptionCacheKey<V> cacheKey, Optional<V> value) {
        checkRankOption(cacheKey, RankOption.SPECIFIC_RANK);
        checkArgument(value.isPresent(), "Value cannot be empty");
        checkNotNull(value);
        String stringValue = dataConverter.convertValueToString(value.get());
        delegate.write(cacheKey, stringValue);
    }


    @Override
    public <V> Optional<String> deleteValue(OptionCacheKey<V> cacheKey) {
        checkRankOption(cacheKey, RankOption.SPECIFIC_RANK);
        return delegate.deleteValue(cacheKey);
    }


    @SuppressWarnings("unchecked")

    @Override
    public <V> Optional<V> getValue(OptionCacheKey<V> cacheKey) {
        Optional<String> optionalStringValue;

        switch (cacheKey.getRankOption()) {
            case HIGHEST_RANK:
                optionalStringValue = getRankedValue(cacheKey, false);
                break;
            case LOWEST_RANK:
                optionalStringValue = getRankedValue(cacheKey, true);
                break;
            case SPECIFIC_RANK:
                optionalStringValue = getStringValue(cacheKey);
                break;
            default:
                throw new OptionException("Unrecognised rankOption");
        }

        if (optionalStringValue.isPresent()) {
            // use the default value to establish data type
            V defaultValue = cacheKey.getOptionKey()
                    .getDefaultValue();
            if (defaultValue instanceof DataList) {
                Class<?> collectionClass = DataList.class;
                Class<?> elementClass = ((DataList) defaultValue).getEntryClass();
                DataList convertedValue = dataConverter.convertStringToCollection(collectionClass, elementClass, optionalStringValue.get(), ",");
                return Optional.of((V) convertedValue);
            } else {
                Class<V> elementClass = (Class<V>) defaultValue.getClass();
                return Optional.of(dataConverter.convertStringToValue(elementClass, optionalStringValue.get()));
            }
        } else {
            return Optional.empty();
        }
    }

    protected Optional<String> getStringValue(OptionCacheKey cacheKey) {
        return delegate.getValue(cacheKey);
    }


    protected <V> Optional<String> getRankedValue(OptionCacheKey<V> cacheKey, boolean lowest) {
        ImmutableList<String> ranks = cacheKey.getHierarchy()
                .ranksForCurrentUser();
        ImmutableList<String> ranksToUse = (lowest) ? ranks.reverse() : ranks;
        for (String rank : ranksToUse) {
            OptionCacheKey<V> specificKey = new OptionCacheKey<>(cacheKey, rank, RankOption.SPECIFIC_RANK);
            Optional<String> stringValue = getStringValue(specificKey);
            if (stringValue.isPresent()) return stringValue;
        }
        return Optional.empty();
    }


    @Override
    public String connectionUrl() {
        return delegate.connectionUrl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long clear() {
        long count = delegate.count();
        delegate.clear();
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long count() {
        return delegate.count();
    }

    @Override
    public void checkRankOption(OptionCacheKey<?> cacheKey, RankOption expected) {
        if (cacheKey.getRankOption() != expected) {
            throw new OptionKeyException("OptionCacheKey should have RankOption of: " + expected);
        }
    }
}
