package com.cvlievera.stockmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
        exclude = { DataSourceAutoConfiguration.class },
        scanBasePackages = { "com.cvlievera.stockmanagement" }
)
public class CvLieveraStockManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CvLieveraStockManagementApplication.class, args);
    }
}
