package com.xiachen.yishe.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.api.dto.AppointmentCreateRequest;
import com.xiachen.yishe.api.dto.AppointmentView;
import com.xiachen.yishe.api.dto.CreatedResult;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.common.web.PageResult;
import com.xiachen.yishe.domain.entity.Appointment;
import com.xiachen.yishe.domain.entity.AppointmentItem;
import com.xiachen.yishe.domain.entity.Product;
import com.xiachen.yishe.domain.entity.Sku;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.mapper.AppointmentItemMapper;
import com.xiachen.yishe.domain.mapper.AppointmentMapper;
import com.xiachen.yishe.domain.mapper.ProductMapper;
import com.xiachen.yishe.domain.mapper.SkuMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentService {

    private static final String ON = "ON";
    private static final String PENDING = "PENDING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String DONE = "DONE";
    private static final String CANCELED = "CANCELED";
    private static final int MAX_DAYS_AHEAD = 30;

    private final AppointmentMapper appointmentMapper;
    private final AppointmentItemMapper appointmentItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final StoreMapper storeMapper;

    public AppointmentService(AppointmentMapper appointmentMapper, AppointmentItemMapper appointmentItemMapper,
                              SkuMapper skuMapper, ProductMapper productMapper, StoreMapper storeMapper) {
        this.appointmentMapper = appointmentMapper;
        this.appointmentItemMapper = appointmentItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
    }

    public CreatedResult create(long userId, AppointmentCreateRequest request) {
        Store store = storeMapper.selectById(request.storeId());
        if (store == null || !ON.equals(store.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "门店暂不可预约，请换一家");
        }
        validateVisitTime(request, store);
        List<Long> skuIds = validateSkus(request.skuIds());

        Appointment existing = appointmentMapper.selectOne(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getUserId, userId)
                .eq(Appointment::getClientRequestId, request.clientRequestId())
                .last("LIMIT 1"));
        if (existing != null) {
            return new CreatedResult(existing.getId(), existing.getStatus());
        }

        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setStoreId(request.storeId());
        appointment.setVisitDate(request.visitDate());
        appointment.setVisitTime(request.visitTime());
        appointment.setItemCount(skuIds.size());
        appointment.setPhone(request.phone().trim());
        appointment.setContactName(trim(request.contactName()));
        appointment.setRemark(trim(request.remark()));
        appointment.setStatus(PENDING);
        appointment.setClientRequestId(request.clientRequestId());
        try {
            appointmentMapper.insert(appointment);
        } catch (DuplicateKeyException e) {
            Appointment duplicated = appointmentMapper.selectOne(Wrappers.<Appointment>lambdaQuery()
                    .eq(Appointment::getUserId, userId)
                    .eq(Appointment::getClientRequestId, request.clientRequestId())
                    .last("LIMIT 1"));
            if (duplicated != null) {
                return new CreatedResult(duplicated.getId(), duplicated.getStatus());
            }
            throw e;
        }

        for (Long skuId : skuIds) {
            AppointmentItem item = new AppointmentItem();
            item.setAppointmentId(appointment.getId());
            item.setSkuId(skuId);
            item.setStoreId(request.storeId());
            appointmentItemMapper.insert(item);
        }
        return new CreatedResult(appointment.getId(), PENDING);
    }

    public PageResult<AppointmentView> list(long userId, int page, int size) {
        Long total = appointmentMapper.selectCount(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getUserId, userId));
        List<Appointment> rows = appointmentMapper.selectList(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getUserId, userId)
                .orderByDesc(Appointment::getCreatedAt)
                .orderByDesc(Appointment::getId)
                .last("LIMIT " + (long) (page - 1) * size + "," + size));
        return new PageResult<>(buildViews(rows), total == null ? 0 : total, page, size);
    }

    public AppointmentView detail(long userId, long id) {
        Appointment appointment = appointmentMapper.selectOne(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getId, id)
                .eq(Appointment::getUserId, userId)
                .last("LIMIT 1"));
        if (appointment == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return buildViews(List.of(appointment)).getFirst();
    }

    public void cancel(long userId, long id) {
        Appointment appointment = appointmentMapper.selectOne(Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getId, id)
                .eq(Appointment::getUserId, userId)
                .last("LIMIT 1"));
        if (appointment == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (DONE.equals(appointment.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已到店的预约不能取消");
        }
        if (CANCELED.equals(appointment.getStatus())) {
            return;
        }
        appointmentMapper.update(null, Wrappers.<Appointment>lambdaUpdate()
                .eq(Appointment::getId, id)
                .eq(Appointment::getUserId, userId)
                .in(Appointment::getStatus, PENDING, CONFIRMED)
                .set(Appointment::getStatus, CANCELED));
    }

    private void validateVisitTime(AppointmentCreateRequest request, Store store) {
        LocalDate today = LocalDate.now();
        if (request.visitDate().isBefore(today)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择今天或之后的日期");
        }
        if (request.visitDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "最多可预约 " + MAX_DAYS_AHEAD + " 天内");
        }
        LocalTime time = request.visitTime();
        if (time == null) {
            return;
        }
        if (time.getSecond() != 0 || (time.getMinute() != 0 && time.getMinute() != 30)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "时间段按 30 分钟一档");
        }
        if (store.getOpenTime() != null && store.getCloseTime() != null
                && (time.isBefore(store.getOpenTime()) || !time.isBefore(store.getCloseTime()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择营业时间内的时段");
        }
    }

    private List<Long> validateSkus(List<Long> skuIds) {
        List<Long> distinct = skuIds.stream().distinct().toList();
        List<Sku> skus = skuMapper.selectBatchIds(distinct);
        if (skus.size() != distinct.size() || skus.stream().anyMatch(sku -> !ON.equals(sku.getStatus()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "部分商品已下架，请刷新清单");
        }
        Set<Long> productIds = skus.stream().map(Sku::getProductId).collect(Collectors.toSet());
        List<Product> products = productMapper.selectBatchIds(productIds);
        if (products.size() != productIds.size()
                || products.stream().anyMatch(product -> !ON.equals(product.getStatus()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "部分商品已下架，请刷新清单");
        }
        return distinct;
    }

    private List<AppointmentView> buildViews(List<Appointment> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(Appointment::getId).toList();
        Map<Long, Store> stores = storeMapper.selectBatchIds(
                        rows.stream().map(Appointment::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));

        List<AppointmentItem> appointmentItems = appointmentItemMapper.selectList(
                Wrappers.<AppointmentItem>lambdaQuery().in(AppointmentItem::getAppointmentId, ids));
        Set<Long> skuIds = appointmentItems.stream().map(AppointmentItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Sku> skus = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Function.identity()));
        Set<Long> productIds = skus.values().stream().map(Sku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, List<AppointmentItem>> itemsByAppointment = appointmentItems.stream()
                .collect(Collectors.groupingBy(AppointmentItem::getAppointmentId));

        List<AppointmentView> views = new ArrayList<>();
        for (Appointment row : rows) {
            Store store = stores.get(row.getStoreId());
            List<AppointmentView.Item> items = new ArrayList<>();
            for (AppointmentItem item : itemsByAppointment.getOrDefault(row.getId(), List.of())) {
                Sku sku = skus.get(item.getSkuId());
                if (sku == null) {
                    continue;
                }
                Product product = products.get(sku.getProductId());
                items.add(new AppointmentView.Item(sku.getId(), sku.getProductId(),
                        product == null ? "" : product.getTitle(), sku.getColor(), sku.getSize(),
                        product == null ? "" : product.getCoverUrl(), nz(sku.getPriceCent())));
            }
            views.add(new AppointmentView(row.getId(), row.getStatus(), statusText(row.getStatus()),
                    row.getStoreId(), store == null ? "" : store.getName(),
                    store == null ? "" : store.getAddress(), store == null ? "" : store.getPhone(),
                    row.getVisitDate(), row.getVisitTime(), nz(row.getItemCount()), row.getPhone(),
                    row.getContactName(), row.getRemark(), row.getCreatedAt(), items));
        }
        return views;
    }

    private static String statusText(String status) {
        return switch (status) {
            case PENDING -> "待确认";
            case CONFIRMED -> "已确认";
            case DONE -> "已到店";
            case CANCELED -> "已取消";
            default -> status;
        };
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
