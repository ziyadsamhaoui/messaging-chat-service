package com.ziyadsamhaoui.messagingchatservice.config;

import com.ziyadsamhaoui.messagingchatservice.client.HttpUserServiceClient;
import com.ziyadsamhaoui.messagingchatservice.client.UserServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    UserServiceClient userServiceClient(UserServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);

        if (StringUtils.hasText(properties.internalToken())) {
            builder.defaultHeader(properties.internalTokenHeader(), properties.internalToken());
        }

        return new HttpUserServiceClient(builder.build(), properties);
    }
}
