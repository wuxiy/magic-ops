package top.cywu.magicops.diagnosis.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.cywu.magicops.diagnosis.service.ArthasTunnelClient;
import top.cywu.magicops.diagnosis.service.TunnelClient;

/**
 * 诊断中心 Tunnel 装配。注册 {@link TunnelProperties} 与 {@link TunnelClient}。
 */
@Configuration
@EnableConfigurationProperties(TunnelProperties.class)
public class DiagnosisTunnelConfiguration {

    @Bean
    public TunnelClient tunnelClient(TunnelProperties properties) {
        return new ArthasTunnelClient(properties);
    }
}
