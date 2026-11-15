package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("collection_product")
public class CollectionProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long collectionId;

    private Long productId;

    private Integer sortNo;
}
