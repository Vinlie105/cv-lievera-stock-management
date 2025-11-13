package com.cvlievera.stockmanagement.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class helloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello from CV Lievera Stock Management System!";
    }
}
