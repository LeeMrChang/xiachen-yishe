package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("home_theme")
public class HomeTheme {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String coverUrl;

    private String copyText;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private String status;

    private Boolean isCurrent;

    private LocalDateTime createdAt;

    // updated_at 交给 MySQL ON UPDATE 维护，避免把查询到的旧值写回
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
