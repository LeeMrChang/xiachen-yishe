package com.xiachen.yishe.admin.bootstrap;

import com.xiachen.yishe.domain.entity.Staff;
import com.xiachen.yishe.domain.mapper.StaffMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final StaffMapper staffMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminInitializer(StaffMapper staffMapper,
                            PasswordEncoder passwordEncoder,
                            @Value("${admin.init.username:admin}") String username,
                            @Value("${admin.init.password:}") String password) {
        this.staffMapper = staffMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (staffMapper.selectCount(null) > 0) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("staff 表为空且未设置 ADMIN_INIT_PASSWORD，跳过管理员初始化");
            return;
        }
        Staff staff = new Staff();
        staff.setUsername(username);
        staff.setPasswordHash(passwordEncoder.encode(password));
        staff.setDisplayName("店长");
        staff.setRole("ADMIN");
        staff.setStatus("ON");
        staffMapper.insert(staff);
        log.info("已创建初始管理员账号：{}", username);
    }
}
