package kr.itsdev.devjobcollector.controller;

public record ApiErrorResponse(
        int status,
        String code,
        String message,
        String path
) {
}
