package xyz.staffjoy.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

//邮件服务主入口
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})//不需要连接数据库
public class MailApplication {

    public static void main(String[] args) {

        SpringApplication.run(MailApplication.class, args);
    }

}

