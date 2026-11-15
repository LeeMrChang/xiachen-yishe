package com.xiachen.yishe.admin.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.CannedAccessControlList;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.xiachen.yishe.common.exception.BizException;
import com.xiachen.yishe.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class CosImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CosImageStorage.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final COSClient client;
    private final String bucket;
    private final String publicBase;
    private final String keyPrefix;

    public CosImageStorage(@Value("${xc.upload.cos.secret-id:}") String secretId,
                           @Value("${xc.upload.cos.secret-key:}") String secretKey,
                           @Value("${xc.upload.cos.region:ap-guangzhou}") String region,
                           @Value("${xc.upload.cos.bucket:}") String bucket,
                           @Value("${xc.upload.cos.public-base:}") String publicBase,
                           @Value("${xc.upload.cos.key-prefix:}") String keyPrefix) {
        if (isBlank(secretId) || isBlank(secretKey) || isBlank(bucket)) {
            throw new IllegalStateException("COS 未配置：需要 COS_SECRET_ID、COS_SECRET_KEY、COS_BUCKET");
        }
        if (isBlank(keyPrefix)) {
            throw new IllegalStateException("COS 未配置目录：COS_KEY_PREFIX 需为 dev 或 prod");
        }
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(region));
        config.setHttpProtocol(HttpProtocol.https);
        config.setConnectionTimeout(5000);
        config.setSocketTimeout(20000);
        this.client = new COSClient(credentials, config);
        this.bucket = bucket;
        this.publicBase = (isBlank(publicBase)
                ? "https://" + bucket + ".cos." + region + ".myqcloud.com"
                : publicBase).replaceAll("/+$", "");
        this.keyPrefix = keyPrefix.replaceAll("^/+|/+$", "");
        log.info("image storage: cos bucket={} region={} publicBase={} keyPrefix={}",
                bucket, region, this.publicBase, this.keyPrefix);
    }

    public String save(byte[] bytes, String extension) {
        String month = LocalDate.now().format(MONTH);
        String key = keyPrefix + "/" + month + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("png".equals(extension) ? "image/png" : "image/jpeg");
        metadata.setCacheControl("public, max-age=31536000");
        try {
            PutObjectRequest request = new PutObjectRequest(
                    bucket, key, new ByteArrayInputStream(bytes), metadata);
            // 商品图需在小程序中匿名展示
            request.setCannedAcl(CannedAccessControlList.PublicRead);
            client.putObject(request);
        } catch (Exception e) {
            log.error("cos putObject failed, bucket={}, key={}", bucket, key, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "图片上传到对象存储失败，请稍后再试");
        }
        return publicBase + "/" + key;
    }

    @PreDestroy
    public void shutdown() {
        client.shutdown();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
