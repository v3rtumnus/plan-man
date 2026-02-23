package at.v3rtumnus.planman.conf;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantCacheConfigTest {

    private final AssistantCacheConfig config = new AssistantCacheConfig();

    @Test
    void cacheManager_returnsCaffeineCacheManager() {
        CacheManager cacheManager = config.cacheManager();

        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
    }
}
