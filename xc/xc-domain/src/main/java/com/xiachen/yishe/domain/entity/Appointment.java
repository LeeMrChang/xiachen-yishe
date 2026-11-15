package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("appointment")
public class Appointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long storeId;

    private LocalDate visitDate;

    private LocalTime visitTime;

    private Integer itemCount;

    private String phone;

    private String contactName;

    private String remark;

    private String status;

    private String clientRequestId;

    private LocalDateTime createdAt;

    // updated_at 交给 MySQL ON UPDATE 维护，避免把查询到的旧值写回
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
