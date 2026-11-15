package com.xiachen.yishe.domain.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 收藏/点赞共用一套多态读写。表名与计数列由服务端的枚举常量传入，不接受外部输入。
 */
public interface UserMarkMapper {

    @Insert("INSERT IGNORE INTO ${table} (user_id, target_type, target_id) "
            + "VALUES (#{userId}, #{targetType}, #{targetId})")
    int insertIgnore(@Param("table") String table,
                     @Param("userId") long userId,
                     @Param("targetType") int targetType,
                     @Param("targetId") long targetId);

    @Delete("DELETE FROM ${table} WHERE user_id = #{userId} AND target_type = #{targetType} "
            + "AND target_id = #{targetId}")
    int delete(@Param("table") String table,
               @Param("userId") long userId,
               @Param("targetType") int targetType,
               @Param("targetId") long targetId);

    @Select("SELECT COUNT(1) FROM ${table} WHERE user_id = #{userId} AND target_type = #{targetType} "
            + "AND target_id = #{targetId}")
    int exists(@Param("table") String table,
               @Param("userId") long userId,
               @Param("targetType") int targetType,
               @Param("targetId") long targetId);

    @Select("SELECT target_id FROM ${table} WHERE user_id = #{userId} AND target_type = #{targetType} "
            + "ORDER BY created_at DESC, id DESC")
    List<Long> targetIds(@Param("table") String table,
                         @Param("userId") long userId,
                         @Param("targetType") int targetType);

    @Update("UPDATE ${table} SET ${column} = GREATEST(${column} + #{delta}, 0) WHERE id = #{id}")
    int bumpTargetCount(@Param("table") String table,
                        @Param("column") String column,
                        @Param("id") long id,
                        @Param("delta") int delta);
}
