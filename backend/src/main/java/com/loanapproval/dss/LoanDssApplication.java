package com.loanapproval.dss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoanDssApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanDssApplication.class, args);
    }
}
