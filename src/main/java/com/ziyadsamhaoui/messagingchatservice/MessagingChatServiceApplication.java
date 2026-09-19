package com.ziyadsamhaoui.messagingchatservice;

import com.ziyadsamhaoui.messagingchatservice.config.ChatProperties;
import com.ziyadsamhaoui.messagingchatservice.config.UserServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ ChatProperties.class, UserServiceProperties.class })
public class MessagingChatServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingChatServiceApplication.class, args);
    }

}
