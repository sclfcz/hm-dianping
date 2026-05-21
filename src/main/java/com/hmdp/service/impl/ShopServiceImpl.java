package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    //线程池，负责缓存重建
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private CacheClient cacheClient;

    /**
     * 根据 id 查询店铺并加入缓存
     * @param id 店铺 id
     * @return 查询结果
     */
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);

        //缓存击穿(互斥锁)
        //Shop shop = queryWithMutex(id);

        //缓存击穿(逻辑过期)
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        return Result.ok(shop);
    }

//    /**
//     * 缓存击穿：使用互斥锁重建缓存(也包含了缓存击穿的逻辑)
//     * @param id 店铺 id
//     * @return 店铺信息
//     */
//    public Shop queryWithMutex(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//
//        //1.从 redis 中查询数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断数据是否存在，存在则直接返回
//        //1. 不是 null
//        //2. 不是空字符串 ""
//        //3. 不是只包含空白字符，比如 "   "
//        if (StringUtils.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        //命中的是空字符串，说明店铺不存在
//        if (shopJson != null) {
//            return null;
//        }
//
//        //3.实现缓存重建
//        Shop shop = null;
//        boolean isLocked = false;
//        try {
//            //3.1.尝试获取互斥锁
//            isLocked = tryLock(lockKey);
//            if (!isLocked) {
//                //3.2.获取锁失败，休眠一段时间后重试
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//
//            //3.3.获取锁成功后，再次检查缓存中是否已有数据
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StringUtils.isNotBlank(shopJson)) {
//                return JSONUtil.toBean(shopJson, Shop.class);
//            }
//            if (shopJson != null) {
//                return null;
//            }
//
//            //3.4.查询数据库
//            shop = getById(id);
//            //模拟重建延迟
//            Thread.sleep(200);
//            if (shop == null) {
//                //数据库中不存在，写入空值缓存
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//
//            //3.5.数据库中存在，写入 redis
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            throw new RuntimeException(e);
//        } finally {
//            //3.6.只有获取到锁的线程才能释放锁
//            if (isLocked) {
//                unlock(lockKey);
//            }
//        }
//        //4.返回店铺信息
//        return shop;
//    }


//    /**
//     * 缓存击穿：使用逻辑过期
//     * @param id 店铺 id
//     * @return 店铺信息
//     */
//    public Shop queryWithLogicalExpire(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//
//        //1.从 redis 中查询数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断数据是否存在，不存在则直接返回
//        if (StringUtils.isBlank(shopJson)) {
//            return null;
//        }
//
//        //3.存在，需要先把json 反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        //4.判断是否过期
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            //4.1.未过期，直接返回店铺信息
//            return shop;
//        }
//
//        //4.2.已过期，重建缓存
//        //5.实现缓存重建
//        //5.1.尝试获取互斥锁
//        boolean isLocked = tryLock(lockKey);
//        if (isLocked) {
//            //5.2.获取锁成功后，异步重建缓存
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//                try {
//                    saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //重建完成后，释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//        //5.3.本线程返回旧数据
//        return shop;
//    }
    
//    /**
//     * 缓存穿透：缓存空值
//     * @param id 店铺 id
//     * @return 店铺信息
//     */
//    public Shop queryWithPassThrough(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//
//        //1.从 redis 中查询数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//
//        //2.判断数据是否存在，存在则直接返回
//        //1. 不是 null
//        //2. 不是空字符串 ""
//        //3. 不是只包含空白字符，比如 "   "
//        if (StringUtils.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        //命中的是空字符串，说明店铺不存在
//        if (shopJson != null) {
//            return null;
//        }
//
//        //3.不存在，查询数据库
//        Shop shop = getById(id);
//
//        //4.数据库中不存在，写入空值缓存
//        if (shop == null) {
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//
//        //5.数据库中存在，写入 redis
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        //6.返回店铺信息
//        return shop;
//    }

//    /**
//     * 尝试获取锁
//     * @param key 锁 key
//     * @return 是否获取成功
//     */
//    private boolean tryLock(String key) {
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * 释放锁
//     * @param key 锁 key
//     */
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    /**
//     * 将店铺信息保存到 redis 中
//     * @param id 店铺 id
//     * @param expireSeconds 过期时间
//     * @throws InterruptedException
//     */
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException{
//        //1.获取店铺信息
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        RedisData redisData = new RedisData();
//        //2.封装数据
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        //3.写入 redis
//        if (shop == null) {
//            return;
//        }
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData), expireSeconds, TimeUnit.SECONDS);
//    }

    /**
     * 更新店铺信息
     * @param shop 店铺信息
     * @return 更新结果
     */
    @Transactional
    public Result update(Shop shop) {
        //1.获取店铺 id
        Long id = shop.getId();

        //2.判断店铺 id 是否为空
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        //3.更新数据库
        updateById(shop);

        //4.删除 redis 缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);

        //5.返回
        return Result.ok();
    }

    /**
     * 根据类型查询店铺
     * @param typeId 店铺类型 id
     * @param current 当前页
     * @param x x坐标
     * @param y y坐标
     * @return 店铺信息
     */
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询 Redis GEO，按照距离排序并取到当前页结尾
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().radius(
                key,
                new Circle(new Point(x, y), new Distance(5000)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(end)
        );

        // 4.解析出当前页的店铺 id 和距离
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(list.size());
        List<Distance> distances = new ArrayList<>(list.size());
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distances.add(result.getDistance());
        });
        String idsStr = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        // 5.根据 id 查询店铺，并保持 Redis GEO 返回的距离顺序
        List<Shop> shops = query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idsStr + ")")
                .list();
        for (int i = 0; i < shops.size(); i++) {
            shops.get(i).setDistance(distances.get(i).getValue());
        }

        return Result.ok(shops);
    }
}
