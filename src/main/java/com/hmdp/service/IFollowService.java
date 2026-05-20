package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注或者取关
     */
    Result follow(Long id, boolean isFollow);

    /**
     * 判断是否关注
     */
    Result isFollow(Long id);

    /**
     * 获取共同关注好友
     */
    Result followCommons(Long id);
}
