package com.xiachen.yishe.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiachen.yishe.domain.entity.TryonItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TryonItemMapper extends BaseMapper<TryonItem> {

    @Insert("INSERT INTO tryon_item (user_id, sku_id, store_id) "
            + "VALUES (#{userId}, #{skuId}, #{storeId}) "
            + "ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP(3)")
    int upsert(@Param("userId") long userId,
               @Param("skuId") long skuId,
               @Param("storeId") long storeId);

    @Select("SELECT * FROM tryon_item WHERE user_id = #{userId} AND sku_id = #{skuId} "
            + "AND store_id = #{storeId} LIMIT 1")
    TryonItem find(@Param("userId") long userId,
                   @Param("skuId") long skuId,
                   @Param("storeId") long storeId);

    @Select("SELECT COUNT(1) FROM tryon_item t JOIN sku s ON s.id = t.sku_id "
            + "WHERE t.user_id = #{userId} AND t.store_id = #{storeId} AND s.product_id = #{productId}")
    int countProduct(@Param("userId") long userId,
                     @Param("productId") long productId,
                     @Param("storeId") long storeId);

    @Update("UPDATE tryon_item SET store_id = #{storeId} WHERE id = #{id} AND user_id = #{userId}")
    int moveOwned(@Param("id") long id,
                  @Param("userId") long userId,
                  @Param("storeId") long storeId);

    @Delete("DELETE FROM tryon_item WHERE id = #{id} AND user_id = #{userId}")
    int deleteOwned(@Param("id") long id,
                    @Param("userId") long userId);
}
