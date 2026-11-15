package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("store")
public class Store {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String code;

    private String address;

    private String phone;

    private LocalTime openTime;

    private LocalTime closeTime;

    private BigDecimal lat;

    private BigDecimal lng;

    private String coverUrl;

    private String status;

    private Integer sortNo;

    private LocalDateTime createdAt;

    // updated_at 交给 MySQL ON UPDATE 维护，避免把查询到的旧值写回
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
