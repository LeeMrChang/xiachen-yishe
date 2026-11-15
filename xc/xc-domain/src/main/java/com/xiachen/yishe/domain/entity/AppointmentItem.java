package com.xiachen.yishe.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appointment_item")
public class AppointmentItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long skuId;

    private Long storeId;

    private LocalDateTime createdAt;
}
