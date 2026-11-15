package com.xiachen.yishe.admin.service;

import com.xiachen.yishe.admin.dto.UploadResult;
import com.xiachen.yishe.admin.storage.CosImageStorage;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class UploadService {

    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final CosImageStorage storage;

    public UploadService(CosImageStorage storage) {
        this.storage = storage;
    }

    public UploadResult store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片不能超过 8MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "图片读取失败，请重试");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "无法识别的图片，请上传 JPG 或 PNG");
        }
        String url = storage.save(bytes, extensionOf(file));
        return new UploadResult(url, image.getWidth(), image.getHeight());
    }

    private static String extensionOf(MultipartFile file) {
        String contentType = file.getContentType();
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if ("image/png".equals(contentType) || name.endsWith(".png")) {
            return "png";
        }
        if ("image/jpeg".equals(contentType) || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "jpg";
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 JPG 或 PNG 图片");
    }
}
