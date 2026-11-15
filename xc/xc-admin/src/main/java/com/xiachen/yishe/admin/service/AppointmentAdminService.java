package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiachen.yishe.admin.dto.AppointmentBoardDetail;
import com.xiachen.yishe.admin.dto.AppointmentRow;
import com.xiachen.yishe.admin.dto.StoreOption;
import com.xiachen.yishe.admin.security.AdminContext;
import com.xiachen.yishe.admin.security.StaffPrincipal;
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
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AppointmentAdminService {

    private static final String PENDING = "PENDING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String DONE = "DONE";
    private static final String CANCELED = "CANCELED";
    private static final Set<String> ALLOWED_TARGETS = Set.of(CONFIRMED, DONE, CANCELED);

    private final AppointmentMapper appointmentMapper;
    private final AppointmentItemMapper appointmentItemMapper;
    private final SkuMapper skuMapper;
    private final ProductMapper productMapper;
    private final StoreMapper storeMapper;

    public AppointmentAdminService(AppointmentMapper appointmentMapper,
                                   AppointmentItemMapper appointmentItemMapper,
                                   SkuMapper skuMapper,
                                   ProductMapper productMapper,
                                   StoreMapper storeMapper) {
        this.appointmentMapper = appointmentMapper;
        this.appointmentItemMapper = appointmentItemMapper;
        this.skuMapper = skuMapper;
        this.productMapper = productMapper;
        this.storeMapper = storeMapper;
    }

    public PageResult<AppointmentRow> list(LocalDate date, String status, Long storeId, int page, int size) {
        Long scopedStoreId = scopedStoreId(storeId);
        var query = Wrappers.<Appointment>lambdaQuery()
                .eq(Appointment::getVisitDate, date == null ? LocalDate.now() : date)
                .orderByAsc(Appointment::getVisitTime)
                .orderByAsc(Appointment::getId);
        if (hasText(status)) {
            query.eq(Appointment::getStatus, status);
        }
        if (scopedStoreId != null) {
            query.eq(Appointment::getStoreId, scopedStoreId);
        }
        IPage<Appointment> result = appointmentMapper.selectPage(new Page<>(page, size), query);
        List<Appointment> rows = result.getRecords();
        if (rows.isEmpty()) {
            return new PageResult<>(List.of(), result.getTotal(), page, size);
        }
        Map<Long, Store> stores = storeMapper.selectBatchIds(
                        rows.stream().map(Appointment::getStoreId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Store::getId, Function.identity()));
        List<AppointmentRow> views = rows.stream()
                .map(row -> new AppointmentRow(row.getId(), row.getVisitDate(), row.getVisitTime(),
                        row.getStatus(), statusText(row.getStatus()), row.getStoreId(),
                        stores.containsKey(row.getStoreId()) ? stores.get(row.getStoreId()).getName() : "",
                        nz(row.getItemCount()), row.getPhone(), row.getContactName(), row.getRemark(),
                        row.getCreatedAt()))
                .toList();
        return new PageResult<>(views, result.getTotal(), page, size);
    }

    public AppointmentBoardDetail detail(Long id) {
        Appointment appointment = requireAppointment(id);
        checkStore(appointment.getStoreId());
        return buildDetail(appointment);
    }

    public StatusChange updateStatus(Long id, String targetStatus) {
        if (!ALLOWED_TARGETS.contains(targetStatus)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的预约状态");
        }
        Appointment appointment = requireAppointment(id);
        checkStore(appointment.getStoreId());
        String from = appointment.getStatus();
        if (targetStatus.equals(from)) {
            return new StatusChange(buildDetail(appointment), from);
        }
        boolean allowed = switch (targetStatus) {
            case CONFIRMED -> PENDING.equals(from);
            case DONE -> CONFIRMED.equals(from);
            case CANCELED -> PENDING.equals(from) || CONFIRMED.equals(from);
            default -> false;
        };
        if (!allowed) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "当前状态「" + statusText(from) + "」不能改为「" + statusText(targetStatus) + "」");
        }
        appointmentMapper.update(null, Wrappers.<Appointment>lambdaUpdate()
                .eq(Appointment::getId, id)
                .eq(Appointment::getStatus, from)
                .set(Appointment::getStatus, targetStatus));
        Appointment fresh = requireAppointment(id);
        return new StatusChange(buildDetail(fresh), from);
    }

    public List<StoreOption> stores() {
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .eq(Store::getStatus, "ON")
                        .orderByAsc(Store::getSortNo))
                .stream()
                .map(store -> new StoreOption(store.getId(), store.getName(), store.getAddress()))
                .toList();
    }

    private AppointmentBoardDetail buildDetail(Appointment appointment) {
        Store store = storeMapper.selectById(appointment.getStoreId());
        List<AppointmentItem> items = appointmentItemMapper.selectList(Wrappers.<AppointmentItem>lambdaQuery()
                .eq(AppointmentItem::getAppointmentId, appointment.getId())
                .orderByAsc(AppointmentItem::getId));
        Set<Long> skuIds = items.stream().map(AppointmentItem::getSkuId).collect(Collectors.toSet());
        Map<Long, Sku> skus = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectBatchIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, Function.identity()));
        Set<Long> productIds = skus.values().stream().map(Sku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<AppointmentBoardDetail.Item> views = new ArrayList<>();
        for (AppointmentItem item : items) {
            Sku sku = skus.get(item.getSkuId());
            if (sku == null) {
                continue;
            }
            Product product = products.get(sku.getProductId());
            views.add(new AppointmentBoardDetail.Item(sku.getId(), sku.getProductId(),
                    product == null ? "" : product.getTitle(), sku.getColor(), sku.getSize(),
                    product == null ? "" : product.getCoverUrl(), nz(sku.getPriceCent())));
        }
        return new AppointmentBoardDetail(appointment.getId(), appointment.getStatus(),
                statusText(appointment.getStatus()), appointment.getStoreId(),
                store == null ? "" : store.getName(), store == null ? "" : store.getAddress(),
                store == null ? "" : store.getPhone(), appointment.getVisitDate(), appointment.getVisitTime(),
                nz(appointment.getItemCount()), appointment.getPhone(), appointment.getContactName(),
                appointment.getRemark(), appointment.getCreatedAt(), views);
    }

    private Appointment requireAppointment(Long id) {
        Appointment appointment = appointmentMapper.selectById(id);
        if (appointment == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return appointment;
    }

    private Long scopedStoreId(Long requested) {
        StaffPrincipal principal = principal();
        if ("MANAGER".equals(principal.role()) && principal.storeId() != null) {
            if (requested != null && !requested.equals(principal.storeId())) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能查看本店预约");
            }
            return principal.storeId();
        }
        return requested;
    }

    private void checkStore(Long storeId) {
        StaffPrincipal principal = principal();
        if ("MANAGER".equals(principal.role()) && principal.storeId() != null
                && !principal.storeId().equals(storeId)) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
    }

    private StaffPrincipal principal() {
        StaffPrincipal principal = AdminContext.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    public record StatusChange(AppointmentBoardDetail appointment, String fromStatus) {
    }
}
