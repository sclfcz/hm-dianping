package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 子涵
 * @since 2026-4
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询店铺类型列表并加入缓存
     * @return
     */
    Result queryTypeList();
}
