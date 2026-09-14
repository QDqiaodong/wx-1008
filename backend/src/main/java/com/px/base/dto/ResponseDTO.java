package com.px.base.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseDTO<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> ResponseDTO<T> success(T data) {
        return ResponseDTO.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .build();
    }

    public static <T> ResponseDTO<T> success(String message, T data) {
        return ResponseDTO.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ResponseDTO<T> error(Integer code, String message) {
        return ResponseDTO.<T>builder()
                .code(code)
                .message(message)
                .build();
    }

    public static <T> ResponseDTO<T> error(String message) {
        return ResponseDTO.<T>builder()
                .code(500)
                .message(message)
                .build();
    }
}
