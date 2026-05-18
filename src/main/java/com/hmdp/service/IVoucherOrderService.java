package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author 子慕
 * @since 2026-5
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 下单结果
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 创建订单（一人一单 + 乐观锁扣库存 + 事务）
     *
     * @param voucherOrder 订单信息
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
