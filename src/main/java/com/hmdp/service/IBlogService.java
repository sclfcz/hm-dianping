package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 子涵
 * @since 2026-5
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门探店博文
     */
    /**
     * 查询热门探店博文
     */
    Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current);

    /**
     * 根据id查询探店博文
     */
    /**
     * 根据id查询探店博文
     */
    Result queryBlogById(Long id);

    /**
     * 点赞
     */
    Result likeBlog(Long id);

    /**
     * 查询探店博文的点赞排行榜
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存探店博文
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注的博主的探店博文
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
