package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    String key = "follows:";
    /**
     * 关注或者取关
     */
    public Result follow(Long followUserId, boolean isFollow) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断是关注还是取关
        if (isFollow) {
            //3.关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            //4.放到redis中
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key + userId, followUserId.toString());
            }
        }
        //4.取关
        else{
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key + userId, followUserId.toString());
            }
        }

        return Result.ok();
    }

    /**
     * 判断是否关注
     */
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //3.判断
        return Result.ok(count > 0);
    }

    /**
     * 获取共同关注好友
     */
    public Result followCommons(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //2.将登录用户与关注用户的关注列表取交集
        String key2 = "follows:" + id;
        Set<String> intersection = stringRedisTemplate.opsForSet().intersect(key, key2);
        //3.解析出id
        List<Long> commonIds = intersection.stream().map(Long::valueOf).collect(Collectors.toList());
        if (commonIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //4.根据交集id查询用户
        List<UserDTO> commonUsers = userService.listByIds(commonIds)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //5.包装返回
        return Result.ok(commonUsers);
    }
}
