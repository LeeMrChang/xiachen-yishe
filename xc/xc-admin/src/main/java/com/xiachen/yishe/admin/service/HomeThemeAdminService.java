package com.xiachen.yishe.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.admin.dto.HomeModulesRequest;
import com.xiachen.yishe.admin.dto.HomeThemeDetail;
import com.xiachen.yishe.admin.dto.HomeThemeRow;
import com.xiachen.yishe.admin.dto.HomeThemeSaveRequest;
import com.xiachen.yishe.admin.support.CacheEvictor;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.HomeModule;
import com.xiachen.yishe.domain.entity.HomeTheme;
import com.xiachen.yishe.domain.mapper.CollectionMapper;
import com.xiachen.yishe.domain.mapper.HomeModuleMapper;
import com.xiachen.yishe.domain.mapper.HomeThemeMapper;
import com.xiachen.yishe.domain.mapper.StoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HomeThemeAdminService {

    private static final String DRAFT = "DRAFT";
    private static final String ON = "ON";
    private static final String OFF = "OFF";
    private static final String BANNER = "BANNER";
    private static final String COLLECTION = "COLLECTION";
    private static final String STORE = "STORE";
    private static final Set<String> TYPES = Set.of("BANNER", "COLLECTION", "RECOMMEND", "STORE", "LOOK");
    private static final Set<String> SINGLE_TYPES = Set.of("BANNER", "RECOMMEND", "STORE", "LOOK");
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "BANNER", "首屏大图",
            "RECOMMEND", "店长推荐",
            "COLLECTION", "系列",
            "LOOK", "造型灵感",
            "STORE", "门店");

    private final HomeThemeMapper homeThemeMapper;
    private final HomeModuleMapper homeModuleMapper;
    private final CollectionMapper collectionMapper;
    private final StoreMapper storeMapper;
    private final CacheEvictor cacheEvictor;

    public HomeThemeAdminService(HomeThemeMapper homeThemeMapper, HomeModuleMapper homeModuleMapper,
                                 CollectionMapper collectionMapper, StoreMapper storeMapper,
                                 CacheEvictor cacheEvictor) {
        this.homeThemeMapper = homeThemeMapper;
        this.homeModuleMapper = homeModuleMapper;
        this.collectionMapper = collectionMapper;
        this.storeMapper = storeMapper;
        this.cacheEvictor = cacheEvictor;
    }

    public List<HomeThemeRow> list() {
        List<HomeTheme> themes = homeThemeMapper.selectList(Wrappers.<HomeTheme>lambdaQuery()
                .orderByDesc(HomeTheme::getIsCurrent)
                .orderByDesc(HomeTheme::getId));
        Map<Long, Long> counts = themes.isEmpty() ? Map.of()
                : homeModuleMapper.selectList(Wrappers.<HomeModule>lambdaQuery()
                        .in(HomeModule::getThemeId, themes.stream().map(HomeTheme::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(HomeModule::getThemeId, Collectors.counting()));
        return themes.stream()
                .map(theme -> new HomeThemeRow(theme.getId(), theme.getName(), theme.getCoverUrl(),
                        theme.getCopyText(), theme.getStartAt(), theme.getEndAt(), theme.getStatus(),
                        Boolean.TRUE.equals(theme.getIsCurrent()),
                        counts.getOrDefault(theme.getId(), 0L).intValue(), theme.getUpdatedAt()))
                .toList();
    }

    public HomeThemeDetail detail(Long id) {
        return buildDetail(requireTheme(id));
    }

    @Transactional
    public HomeThemeDetail create(HomeThemeSaveRequest request) {
        validateWindow(request);
        HomeTheme theme = new HomeTheme();
        theme.setStatus(request.status() == null ? DRAFT : request.status());
        theme.setIsCurrent(false);
        applyFields(theme, request);
        homeThemeMapper.insert(theme);
        cacheEvictor.evictCatalog();
        return detail(theme.getId());
    }

    @Transactional
    public HomeThemeDetail update(Long id, HomeThemeSaveRequest request) {
        HomeTheme theme = requireTheme(id);
        validateWindow(request);
        String status = request.status() == null ? theme.getStatus() : request.status();
        if (!ON.equals(status) && Boolean.TRUE.equals(theme.getIsCurrent())) {
            theme.setIsCurrent(false);
        }
        theme.setStatus(status);
        applyFields(theme, request);
        homeThemeMapper.updateById(theme);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public HomeThemeDetail activate(Long id) {
        HomeTheme theme = requireTheme(id);
        if (!ON.equals(theme.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请先把主题状态改为「启用」再设为当前主题");
        }
        requireHero(theme);
        homeThemeMapper.update(null, Wrappers.<HomeTheme>lambdaUpdate()
                .eq(HomeTheme::getIsCurrent, true)
                .set(HomeTheme::getIsCurrent, false));
        theme.setIsCurrent(true);
        homeThemeMapper.updateById(theme);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    @Transactional
    public HomeThemeDetail offline(Long id) {
        HomeTheme theme = requireTheme(id);
        theme.setStatus(OFF);
        theme.setIsCurrent(false);
        homeThemeMapper.updateById(theme);
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    /** 全量保存模块：数组顺序即首页展示顺序 */
    @Transactional
    public HomeThemeDetail saveModules(Long id, HomeModulesRequest request) {
        HomeTheme theme = requireTheme(id);
        List<HomeModulesRequest.Item> items = request.modules();
        Map<String, Integer> typeCounts = new HashMap<>();
        Set<Long> collectionRefs = new HashSet<>();
        for (HomeModulesRequest.Item item : items) {
            String type = item.type().trim().toUpperCase();
            if (!TYPES.contains(type)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "不支持的模块类型：" + item.type());
            }
            typeCounts.merge(type, 1, Integer::sum);
            if (SINGLE_TYPES.contains(type) && typeCounts.get(type) > 1) {
                throw new BizException(ErrorCode.PARAM_ERROR,
                        "「" + TYPE_LABELS.get(type) + "」模块只能有一个");
            }
            if (COLLECTION.equals(type) && typeCounts.get(type) > 3) {
                throw new BizException(ErrorCode.PARAM_ERROR, "系列模块最多 3 个");
            }
            if (COLLECTION.equals(type) && (item.refId() == null || collectionMapper.selectById(item.refId()) == null)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "请选择有效的系列");
            }
            if (COLLECTION.equals(type) && !collectionRefs.add(item.refId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "同一个系列只能添加一次");
            }
            if (STORE.equals(type) && item.refId() != null && storeMapper.selectById(item.refId()) == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "请选择有效的门店");
            }
        }
        homeModuleMapper.delete(Wrappers.<HomeModule>lambdaQuery().eq(HomeModule::getThemeId, id));
        int index = 1;
        for (HomeModulesRequest.Item item : items) {
            String type = item.type().trim().toUpperCase();
            HomeModule module = new HomeModule();
            module.setThemeId(id);
            module.setType(type);
            module.setRefId(COLLECTION.equals(type) || STORE.equals(type) ? item.refId() : null);
            module.setImageUrl(item.imageUrl() == null ? "" : item.imageUrl().trim());
            module.setSortNo(index++);
            module.setEnabled(item.enabled() == null || item.enabled());
            homeModuleMapper.insert(module);
        }
        cacheEvictor.evictCatalog();
        return detail(id);
    }

    private HomeThemeDetail buildDetail(HomeTheme theme) {
        List<HomeModule> modules = homeModuleMapper.selectList(Wrappers.<HomeModule>lambdaQuery()
                .eq(HomeModule::getThemeId, theme.getId())
                .orderByAsc(HomeModule::getSortNo)
                .orderByAsc(HomeModule::getId));
        Map<Long, String> collectionNames = new HashMap<>();
        Map<Long, String> storeNames = new HashMap<>();
        List<Long> collectionIds = modules.stream()
                .filter(m -> COLLECTION.equals(m.getType()) && m.getRefId() != null)
                .map(HomeModule::getRefId).distinct().toList();
        if (!collectionIds.isEmpty()) {
            collectionMapper.selectBatchIds(collectionIds)
                    .forEach(c -> collectionNames.put(c.getId(), c.getName()));
        }
        List<Long> storeIds = modules.stream()
                .filter(m -> STORE.equals(m.getType()) && m.getRefId() != null)
                .map(HomeModule::getRefId).distinct().toList();
        if (!storeIds.isEmpty()) {
            storeMapper.selectBatchIds(storeIds)
                    .forEach(s -> storeNames.put(s.getId(), s.getName()));
        }
        List<HomeThemeDetail.Module> views = new ArrayList<>();
        for (HomeModule module : modules) {
            String refName = switch (module.getType()) {
                case COLLECTION -> collectionNames.get(module.getRefId());
                case STORE -> module.getRefId() == null ? "默认门店" : storeNames.get(module.getRefId());
                default -> null;
            };
            views.add(new HomeThemeDetail.Module(module.getId(), module.getType(),
                    TYPE_LABELS.getOrDefault(module.getType(), module.getType()), module.getRefId(), refName,
                    module.getImageUrl(), nz(module.getSortNo()), Boolean.TRUE.equals(module.getEnabled())));
        }
        return new HomeThemeDetail(theme.getId(), theme.getName(), theme.getCoverUrl(), theme.getCopyText(),
                theme.getStartAt(), theme.getEndAt(), theme.getStatus(),
                Boolean.TRUE.equals(theme.getIsCurrent()), theme.getUpdatedAt(), views);
    }

    private void requireHero(HomeTheme theme) {
        boolean hasBanner = homeModuleMapper.selectCount(Wrappers.<HomeModule>lambdaQuery()
                .eq(HomeModule::getThemeId, theme.getId())
                .eq(HomeModule::getType, BANNER)
                .eq(HomeModule::getEnabled, true)
                .ne(HomeModule::getImageUrl, "")) > 0;
        boolean hasCover = theme.getCoverUrl() != null && !theme.getCoverUrl().isBlank();
        if (!hasBanner && !hasCover) {
            throw new BizException(ErrorCode.PARAM_ERROR, "启用前请先上传首屏大图");
        }
    }

    private void validateWindow(HomeThemeSaveRequest request) {
        LocalDateTime start = request.startAt();
        LocalDateTime end = request.endAt();
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "结束时间必须晚于开始时间");
        }
    }

    private static void applyFields(HomeTheme theme, HomeThemeSaveRequest request) {
        theme.setName(request.name().trim());
        theme.setCopyText(request.copyText() == null ? "" : request.copyText().trim());
        theme.setCoverUrl(request.coverUrl() == null ? "" : request.coverUrl().trim());
        theme.setStartAt(request.startAt());
        theme.setEndAt(request.endAt());
    }

    private HomeTheme requireTheme(Long id) {
        HomeTheme theme = homeThemeMapper.selectById(id);
        if (theme == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return theme;
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
