package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private ShopServiceImpl shopServiceimpl;

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopServiceimpl.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY+1L, shop, 10L, TimeUnit.SECONDS);
    }
}
