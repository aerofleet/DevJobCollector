package kr.itsdev.devjobcollector.collection.domain;

public enum CollectionStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    EMPTY_SUCCESS,
    FAILED,
    SCHEMA_CHANGED,
    RATE_LIMITED
}
