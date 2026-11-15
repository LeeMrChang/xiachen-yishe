package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("look_item")
public class LookItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lookId;

    private Long productId;

    private Integer sortNo;
}
