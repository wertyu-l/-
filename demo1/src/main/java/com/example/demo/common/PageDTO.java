package com.example.demo.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class PageDTO implements Serializable {

    //用户姓名
    private String username;
    //页码
    private Integer page;
    //每页数量
    private Integer pageSize;

}