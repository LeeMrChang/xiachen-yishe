package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.StoreCoverRequest;
import com.xiachen.yishe.admin.dto.StoreCoverView;
import com.xiachen.yishe.admin.dto.StoreCreateRequest;
import com.xiachen.yishe.admin.dto.StoreStatusRequest;
import com.xiachen.yishe.admin.dto.StoreUpdateRequest;
import com.xiachen.yishe.admin.security.AdminContext;
import com.xiachen.yishe.admin.security.StaffPrincipal;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.Store;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
public class StoreAdminService {

    private static final String ON = "ON";
    private static final String OFF = "OFF";

    private final StoreMapper storeMapper;
    private final CacheEvictor cacheEvictor;

    public StoreAdminService(StoreMapper storeMapper, CacheEvictor cacheEvictor) {
        this.storeMapper = storeMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public List<StoreCoverView> covers() {
        return storeMapper.selectList(Wrappers.<Store>lambdaQuery()
                        .orderByAsc(Store::getSortNo).orderByAsc(Store::getId))
                .stream().map(StoreAdminService::view).toList();
    }

    public StoreCoverView create(StoreCreateRequest request) {
        requireAdmin();
        validateHours(request.openTime(), request.closeTime());
        String code = request.code().trim().toUpperCase();
        if (storeMapper.selectCount(Wrappers.<Store>lambdaQuery().eq(Store::getCode, code)) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "门店编码已存在：" + code);
        }
        Store store = new Store();
        store.setCode(code);
        store.setName(request.name().trim());
        store.setAddress(nz(request.address()));
        store.setPhone(nz(request.phone()));
        store.setOpenTime(request.openTime());
        store.setCloseTime(request.closeTime());
        store.setCoverUrl(nz(request.coverUrl()));
        store.setStatus(ON);
        store.setSortNo(nextSortNo());
        storeMapper.insert(store);
        cacheEvictor.evictCatalog();
        return view(storeMapper.selectById(store.getId()));
    }

    public StoreCoverView update(Long id, StoreUpdateRequest request) {
        requireStore(id);
        requireStoreAccess(id);
        validateHours(request.openTime(), request.closeTime());
        storeMapper.update(null, Wrappers.<Store>lambdaUpdate()
                .eq(Store::getId, id)
                .set(Store::getName, request.name().trim())
                .set(Store::getAddress, nz(request.address()))
                .set(Store::getPhone, nz(request.phone()))
                .set(Store::getOpenTime, request.openTime())
                .set(Store::getCloseTime, request.closeTime()));
        cacheEvictor.evictCatalog();
        return view(storeMapper.selectById(id));
    }

    public StoreCoverView updateStatus(Long id, StoreStatusRequest request) {
        requireAdmin();
        requireStore(id);
        String status = request.status().trim().toUpperCase();
        storeMapper.update(null, Wrappers.<Store>lambdaUpdate()
                .eq(Store::getId, id)
                .set(Store::getStatus, status));
        cacheEvictor.evictCatalog();
        return view(storeMapper.selectById(id));
    }

    public StoreCoverView updateCover(Long id, StoreCoverRequest request) {
        Store store = requireStore(id);
        requireStoreAccess(id);
        store.setCoverUrl(request.coverUrl().trim());
        storeMapper.updateById(store);
        cacheEvictor.evictCatalog();
        return view(store);
    }

    private void validateHours(LocalTime openTime, LocalTime closeTime) {
        if ((openTime == null) != (closeTime == null)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "营业时间需同时填写开始和结束");
        }
        if (openTime != null && !openTime.isBefore(closeTime)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "营业结束时间必须晚于开始时间");
        }
    }

    private int nextSortNo() {
        Store last = storeMapper.selectOne(Wrappers.<Store>lambdaQuery()
                .orderByDesc(Store::getSortNo).orderByDesc(Store::getId).last("LIMIT 1"));
        return last == null || last.getSortNo() == null ? 1 : last.getSortNo() + 1;
    }

    private void requireAdmin() {
        StaffPrincipal principal = AdminContext.get();
        if (principal != null && !"ADMIN".equals(principal.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可新增或下线门店");
        }
    }

    private void requireStoreAccess(Long id) {
        StaffPrincipal principal = AdminContext.get();
        if (principal != null && "MANAGER".equals(principal.role()) && principal.storeId() != null
                && !principal.storeId().equals(id)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能维护本店信息");
        }
    }

    private Store requireStore(Long id) {
        Store store = storeMapper.selectById(id);
        if (store == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "门店不存在");
        }
        return store;
    }

    private static String nz(String value) {
        return value == null ? "" : value.trim();
    }

    private static StoreCoverView view(Store store) {
        return new StoreCoverView(store.getId(), store.getName(), store.getCode(), store.getAddress(),
                store.getPhone(), store.getOpenTime(), store.getCloseTime(), store.getCoverUrl(), store.getStatus());
    }
}
