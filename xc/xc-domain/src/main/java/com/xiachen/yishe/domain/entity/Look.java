package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("look")
public class Look {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private String coverUrl;

    private Integer coverWidth;

    private Integer coverHeight;

    private String status;

    private Integer sortNo;

    private Integer likeCount;

    private Integer favCount;

    private LocalDateTime createdAt;

    // updated_at 交给 MySQL ON UPDATE 维护，避免把查询到的旧值写回
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
