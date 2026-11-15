package com.xiachen.yishe.api.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import com.xiachen.yishe.domain.entity.AppUser;
import com.xiachen.yishe.domain.mapper.AppUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppUserService {

    private static final Logger log = LoggerFactory.getLogger(AppUserService.class);

    private final AppUserMapper appUserMapper;

    public AppUserService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    public AppUser login(String appid, String openid, String unionid) {
        LocalDateTime now = LocalDateTime.now();
        AppUser user = findByOpenid(appid, openid);
        if (user == null) {
            AppUser created = new AppUser();
            created.setAppid(appid);
            created.setOpenid(openid);
            created.setUnionid(unionid);
            created.setLastLoginAt(now);
            try {
                appUserMapper.insert(created);
            } catch (DuplicateKeyException e) {
                log.info("concurrent first login, appid={}", appid);
                user = findByOpenid(appid, openid);
            }
            if (user == null && created.getId() != null) {
                user = appUserMapper.selectById(created.getId());
            }
            if (user == null) {
                throw new BizException(ErrorCode.WX_LOGIN_FAILED);
            }
            return user;
        }

        AppUser patch = new AppUser();
        patch.setId(user.getId());
        patch.setLastLoginAt(now);
        if (user.getUnionid() == null && unionid != null) {
            patch.setUnionid(unionid);
        }
        appUserMapper.updateById(patch);
        user.setLastLoginAt(now);
        if (patch.getUnionid() != null) {
            user.setUnionid(unionid);
        }
        return user;
    }

    public AppUser get(long userId) {
        AppUser user = appUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public AppUser updateProfile(long userId, String nickname, String avatarUrl) {
        if (nickname == null && avatarUrl == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "没有需要保存的资料");
        }
        AppUser patch = new AppUser();
        patch.setId(userId);
        if (nickname != null) {
            String value = nickname.trim();
            if (value.isEmpty()) {
                throw new BizException(ErrorCode.PARAM_ERROR, "昵称不能为空");
            }
            if (value.length() > 64) {
                throw new BizException(ErrorCode.PARAM_ERROR, "昵称最多 64 个字");
            }
            patch.setNickname(value);
        }
        if (avatarUrl != null) {
            String value = avatarUrl.trim();
            if (value.length() > 512) {
                throw new BizException(ErrorCode.PARAM_ERROR, "头像地址过长");
            }
            patch.setAvatarUrl(value);
        }
        appUserMapper.updateById(patch);
        return get(userId);
    }

    private AppUser findByOpenid(String appid, String openid) {
        return appUserMapper.selectOne(Wrappers.<AppUser>lambdaQuery()
                .eq(AppUser::getAppid, appid)
                .eq(AppUser::getOpenid, openid));
    }
}
