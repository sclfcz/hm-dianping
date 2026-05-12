package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * Service implementation.
 * </p>
 *
 * @author 子涵
 * @since 2026-4
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList()
                .range(RedisConstants.SHOP_TYPE_LIST_KEY, 0, -1);

        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            try {
                List<ShopType> shopTypeList = shopTypeJsonList.stream()
                        .map(json -> JSONUtil.toBean(json, ShopType.class))
                        .collect(Collectors.toList());
                return Result.ok(shopTypeList);
            } catch (Exception e) {
                stringRedisTemplate.delete(RedisConstants.SHOP_TYPE_LIST_KEY);
            }
        }

        List<ShopType> shopTypeList = query()
                .orderByAsc("sort")
                .list();

        List<String> jsonList = shopTypeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());

        if (!jsonList.isEmpty()) {
            stringRedisTemplate.opsForList().rightPushAll(RedisConstants.SHOP_TYPE_LIST_KEY, jsonList);
            stringRedisTemplate.expire(RedisConstants.SHOP_TYPE_LIST_KEY, RedisConstants.SHOP_TYPE_LIST_TTL, TimeUnit.MINUTES);
        }

        return Result.ok(shopTypeList);
    }
}
