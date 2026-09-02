package com.example.demo.client.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ClientRequest {

    private String parentName;
    private String childName;
    private String phone;
    private String email;
    private LocalDate childBirthDate;
    private String notes;
    private Boolean frequent;
}
