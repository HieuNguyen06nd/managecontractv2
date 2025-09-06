package com.hieunguyen.ManageContract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseData<T> {
    private final int status;
    private final String message;
    private final T data;

    // Constructor cho trường hợp không có data (chỉ status, message)
    public ResponseData(int status, String message) {
        this.status = status;
        this.message = message;
        this.data = null;
    }

    // Constructor cho trường hợp có data
    public ResponseData(int status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

}
