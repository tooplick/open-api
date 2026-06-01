package com.aiopen.platform.config;

import com.aiopen.platform.modules.user.entity.User;
import com.aiopen.platform.modules.user.service.UserService;
import com.aiopen.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时若不存在管理员账号则创建默认管理员 admin / admin。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userService.getByUsername("admin") != null) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin"));
        admin.setEmail("admin@example.com");
        admin.setRole(UserContext.ROLE_ADMIN);
        admin.setStatus(1);
        admin.setQuota(0L);
        admin.setUsedQuota(0L);
        userService.save(admin);
        log.info("已创建默认管理员账号: admin / admin (请尽快修改密码)");
    }
}
