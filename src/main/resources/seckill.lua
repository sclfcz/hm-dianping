-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1.库存key
local stockKey = "seckill:stock:" .. voucherId
-- 2.2.订单key
local orderKey = "seckill:order:" .. voucherId

-- 3.脚本业务
-- 3.1.获取库存
local stock = redis.call("get", stockKey)
-- 3.2.判断库存是否存在
if (not stock) then
    return 1
end
-- 3.3.判断库存是否充足
if (tonumber(stock) <= 0) then
    -- 3.3.库存不足
    return 1
end

-- 3.4.判断用户是否已经下单
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 3.5.用户已下单
    return 2
end

-- 3.6.扣减库存
redis.call("incrby", stockKey, -1)
-- 3.7.记录用户下单
redis.call("sadd", orderKey, userId)
-- 3.8.返回结果
return 0
