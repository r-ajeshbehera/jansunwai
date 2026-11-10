package com.jansunwai.dto.response;

import com.jansunwai.entity.Ward;

public record WardResponse(Long id, String code, String name) {
    public static WardResponse from(Ward w) {
        return new WardResponse(w.getId(), w.getCode(), w.getName());
    }
}
