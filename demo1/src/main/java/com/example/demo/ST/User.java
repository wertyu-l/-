package com.example.demo.ST;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String role;
    private String userType;
    private Integer userLevel;
    private String department;
    private String position;
    private String phone;
    private String email;
    private Boolean isEnabled;
    private LocalDateTime validUntil;
    private String loginStatus;
}