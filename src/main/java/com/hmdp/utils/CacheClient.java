package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    //线程池，负责缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 将对象写入Redis，设置过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将对象写入Redis，设置逻辑过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 通过缓存穿透查询
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //1.从 redis 中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断数据是否存在，存在则直接返回

        if (StringUtils.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }

        //命中的是空字符串，说明店铺不存在
        if (json != null) {
            return null;
        }

        //3.不存在，查询数据库
        R r = dbFallback.apply(id);

        //4.数据库中不存在，写入空值缓存
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //5.数据库中存在，写入 redis
        this.set(key, r, time, unit);

        //6.返回数据
        return r;
    }

    /**
     * 通过逻辑过期查询
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        //1.从 redis 中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断数据是否存在，不存在则直接返回
        if (StringUtils.isBlank(json)) {
            return null;
        }

        //3.存在，需要先把json 反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject)redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //4.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //4.1.未过期，直接返回店铺信息
            return r;
        }

        //4.2.已过期，重建缓存
        //5.实现缓存重建
        //5.1.尝试获取互斥锁
        boolean isLocked = tryLock(lockKey);
        if (isLocked) {
            //5.2.获取锁成功后，异步重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //重建完成后，释放锁
                    unlock(lockKey);
                }
            });
        }
        //5.3.本线程返回旧数据
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
