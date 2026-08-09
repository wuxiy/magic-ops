package top.cywu.magicops.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import top.cywu.magicops.runtime.packageMgmt.PushSecretAuthenticationFilter;

import java.util.Arrays;

/**
 * Runtime 安全配置。第一阶段使用 Spring Security Basic。
 *
 * <p>切片 32 起，收包端点 {@code POST /api/packages} 额外要求共享密钥认证
 * （{@link PushSecretAuthenticationFilter}，标准 Spring Security 过滤器扩展点）。
 */
@Configuration
public class SecurityConfig {

    @Value("${magicops.runtime.push-secret:}")
    private String pushSecret;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/packages", "/api/packages/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {})
            .addFilterBefore(new PushSecretAuthenticationFilter(pushSecret, prodProfile),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
