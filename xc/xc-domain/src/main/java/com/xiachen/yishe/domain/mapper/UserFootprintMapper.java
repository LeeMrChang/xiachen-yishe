package com.xiachen.yishe.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiachen.yishe.domain.entity.UserFootprint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface UserFootprintMapper extends BaseMapper<UserFootprint> {

    @Insert("INSERT INTO user_footprint (user_id, product_id, store_id, viewed_at) "
            + "VALUES (#{userId}, #{productId}, #{storeId}, CURRENT_TIMESTAMP(3)) "
            + "ON DUPLICATE KEY UPDATE viewed_at = CURRENT_TIMESTAMP(3)")
    int upsert(@Param("userId") long userId,
               @Param("productId") long productId,
               @Param("storeId") long storeId);

    @Select("SELECT * FROM user_footprint WHERE user_id = #{userId} AND viewed_at >= #{since} "
            + "ORDER BY viewed_at DESC, id DESC LIMIT #{limit}")
    List<UserFootprint> recent(@Param("userId") long userId,
                               @Param("since") LocalDateTime since,
                               @Param("limit") int limit);

    @Delete("DELETE FROM user_footprint WHERE user_id = #{userId} AND viewed_at < #{before}")
    int deleteBefore(@Param("userId") long userId,
                     @Param("before") LocalDateTime before);

    @Delete("DELETE FROM user_footprint WHERE user_id = #{userId}")
    int deleteAll(@Param("userId") long userId);
}
