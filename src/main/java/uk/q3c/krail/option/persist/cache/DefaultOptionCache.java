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

package uk.q3c.krail.option.persist.cache;

import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.q3c.krail.option.Option;
import uk.q3c.krail.option.RankOption;
import uk.q3c.krail.option.UserHierarchy;
import uk.q3c.krail.option.bind.OptionModule;
import uk.q3c.krail.option.persist.OptionCache;
import uk.q3c.krail.option.persist.OptionCacheKey;
import uk.q3c.krail.option.persist.OptionCacheProvider;
import uk.q3c.krail.option.persist.OptionDao;
import uk.q3c.krail.option.persist.OptionDaoDelegate;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a cache implementation for {@link Option}.  The {@code get()} methods use the value of the {@link OptionCacheKey} to determine which {@link
 * UserHierarchy} to use, and whether to take the lowest, highest or specific ranked value.
 * <p>
 * Scope is set in {@link OptionModule} but it is assumed that this class needs to be thread safe.
 * <p>
 * <b>NOTE:</b> All values to and from {@link Option} are natively typed.  All values to and from {@link OptionCache}, {@link DefaultOptionCacheLoader} and
 * {@link OptionDaoDelegate} are wrapped in Optional.
 * <p>
 * <p>
 * Created by David Sowerby on 19/02/15.
 */

@ThreadSafe
public class DefaultOptionCache implements OptionCache {

    private static Logger log = LoggerFactory.getLogger(DefaultOptionCache.class);
    private final LoadingCache<OptionCacheKey, Optional<?>> cache;
    private OptionDao daoWrapper;

    @Inject
    public DefaultOptionCache(OptionDao daoWrapper, OptionCacheProvider cacheProvider) {
        this.daoWrapper = daoWrapper;
        cache = cacheProvider.get();
    }

    /**
     * Write value to the store, and updates the cache
     *
     * @param cacheKey unique identifier
     * @param value    the value to set
     * @param <T>      the type of the value
     */
    @Override
    public synchronized <T> void write(OptionCacheKey<T> cacheKey, Optional<T> value) {
        checkNotNull(cacheKey);
        checkNotNull(value);
        // write to store first just in case there's a problem
        log.debug("writing value {} for cacheKey {} via option dao ", value, cacheKey);
        daoWrapper.write(cacheKey, value);

        //invalidate highest / lowest first - cache does clean up as part of write
        cache.invalidate(new OptionCacheKey<>(cacheKey, RankOption.HIGHEST_RANK));
        cache.invalidate(new OptionCacheKey<>(cacheKey, RankOption.LOWEST_RANK));
        cache.put(cacheKey, value);
    }

    @SuppressWarnings("unchecked")
    @Override

    public synchronized <T> Optional<T> get(Optional<T> defaultValue, OptionCacheKey<T> optionCacheKey) {
        checkNotNull(optionCacheKey);
        checkNotNull(defaultValue);
        //this will trigger the cacheLoader if not already in the cache
        Optional<T> optionalValue;
        try {
            optionalValue = (Optional<T>) cache.getUnchecked(optionCacheKey);
            if (!optionalValue.isPresent()) {
                return defaultValue;
            }
        } catch (Throwable e) {
            log.error("Returning default value of {}, exception or error was thrown during load. Exception was:  {}", defaultValue.get(), e);
            return defaultValue;
        }
        if (optionalValue.get()
                .getClass()
                .isAssignableFrom(defaultValue.get()
                        .getClass())) {
            return optionalValue;
        } else {
            log.error("Returning default, option value for {} is of type for {}, but should be of type {}", optionCacheKey, optionalValue.get()
                            .getClass(),
                    defaultValue.getClass());
            return defaultValue;
        }
    }


    @Override

    public synchronized Optional<?> delete(OptionCacheKey<?> optionCacheKey) {
        checkNotNull(optionCacheKey);
        // delete from store first just in case there's a problem
        Optional<?> result = daoWrapper.deleteValue(optionCacheKey);

        //invalidate highest / lowest & specific as these are all now invalid
        cache.invalidate(new OptionCacheKey(optionCacheKey, RankOption.HIGHEST_RANK));
        cache.invalidate(new OptionCacheKey(optionCacheKey, RankOption.LOWEST_RANK));
        cache.invalidate(optionCacheKey);

        // explicit call, there is no write called to trigger clean up
        cache.cleanUp();

        return result;
    }


    @Override
    public synchronized Optional<?> getIfPresent(OptionCacheKey<?> optionCacheKey) {
        checkNotNull(optionCacheKey);
        Optional<?> cachedValue = cache.getIfPresent(optionCacheKey);
        if (cachedValue == null || (!cachedValue.isPresent())) {
            log.debug("cache does not contain a value for {}", optionCacheKey);
            return Optional.empty();
        } else {
            log.debug("cached for {} value is {}", optionCacheKey, cachedValue.get());
            return cachedValue;
        }
    }

    @Override
    public synchronized CacheStats stats() {
        return cache.stats();
    }

    @Override
    public long cacheSize() {
        return cache.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void clear() {
        flush();
        cleanup();
    }

    @Override
    public synchronized void flush() {
        cache.invalidateAll();
    }

    @Override
    public synchronized void cleanup() {
        cache.cleanUp();
    }

    public ConcurrentMap<OptionCacheKey, Optional<?>> asMap() {
        return cache.asMap();
    }


}

