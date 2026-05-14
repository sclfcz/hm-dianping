package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 秒杀优惠券
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //没开始返回错误
            return Result.fail("秒杀尚未开始！");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已结束返回错误
            return Result.fail("秒杀已结束！");
        }

        //3.判断库存
        if (voucher.getStock() < 1) {
            //没库存返回错误
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        synchronized(userId.toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * 创建订单(乐观锁 + 一人一单 + 事务)
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId){
        //4.一人一单(新增数据，只能用悲观锁)
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            //用户已购买过该优惠券
            return Result.fail("用户已购买过该优惠券！");
        }

        //5.扣减库存(乐观锁->用于修改数据)
        //数据库有行锁，确保只有一个线程能更新成功，因为判断和扣减都是在同一个线程内完成的，具有原子性
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) return Result.fail("库存不足！");

        //6.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2.用户id
        voucherOrder.setUserId(userId);
        //6.3.代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        //7.返回订单id
        return Result.ok(orderId);
    }
}
