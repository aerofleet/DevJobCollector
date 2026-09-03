package kr.itsdev.devjobcollector.dto.auth;

public record AccountLinkStartResponse(String authorizationPath, int expiresInSeconds) {
}
