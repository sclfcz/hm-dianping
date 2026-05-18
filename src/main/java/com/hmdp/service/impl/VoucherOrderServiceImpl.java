package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private IVoucherOrderService proxy;

    //lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //阻塞队列
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //在类初始化的时候就开始执行
    @PostConstruct
    public void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    log.error("处理订单异常", e);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        if (!lock.tryLock()) {
            //获取锁失败，返回错误
            log.error("不允许重复下单");
            return;
        }

        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //redisson底层自带lua脚本
            lock.unlock();
        }
    }

    /**
     * 秒杀优惠券
     *
     * @param voucherId 优惠券id
     * @return 下单结果
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在！");
        }

        //2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //没开始返回错误
            return Result.fail("秒杀尚未开始！");
        }

        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已结束返回错误
            return Result.fail("秒杀已结束！");
        }

        //获取用户id
        Long userId = UserHolder.getUser().getId();

        //3.执行lua脚本
        Long execute = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        if (execute == null) {
            return Result.fail("系统繁忙，请稍后再试！");
        }

        //4.判断结果是否为0
        int result = execute.intValue();
        if (result != 0) {
            //代表没有购买资格
            return Result.fail(result == 1 ? "库存不足！" : "请勿重复下单！");
        }

        //5.代表有购买资格，创建订单并将信息保存到阻塞队列里
        //5.1.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.2.订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.3.用户id
        voucherOrder.setUserId(userId);
        //5.4.代金卷id
        voucherOrder.setVoucherId(voucherId);

        //6.获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //7.放入阻塞队列
        boolean success = orderTasks.offer(voucherOrder);
        if (!success) {
            return Result.fail("系统繁忙，请稍后再试！");
        }

        //8.返回订单id
        return Result.ok(orderId);
    }

//    /**
//     * 秒杀优惠券（旧版同步下单逻辑，保留作参考）
//     * @param voucherId
//     * @return
//     */
//    public Result seckillVoucher(Long voucherId) {
//        //1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            //没开始返回错误
//            return Result.fail("秒杀尚未开始！");
//        }
//
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            //已结束返回错误
//            return Result.fail("秒杀已结束！");
//        }
//
//        //3.判断库存
//        if (voucher.getStock() < 1) {
//            //没库存返回错误
//            return Result.fail("库存不足！");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//        //创建锁对象
//        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//
//        if (!lock.tryLock()) {
//            //获取锁失败，返回错误
//            return Result.fail("请勿重复下单！");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
//        finally {
//            //redisson底层自带lua脚本
//            lock.unlock();
//        }
//    }

    /**
     * 创建订单（一人一单 + 乐观锁扣库存 + 事务）
     *
     * @param voucherOrder 订单信息
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //4.一人一单(新增数据，只能用悲观锁)
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            //用户已购买过该优惠券
            log.error("用户已经购买过该优惠券");
            return;
        }

        //5.扣减库存(乐观锁->用于修改数据)
        //数据库有行锁，确保只有一个线程能更新成功，因为判断和扣减都是在同一个线程内完成的，具有原子性
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足");
            return;
        }

        //6.生成订单
        save(voucherOrder);
    }
}
