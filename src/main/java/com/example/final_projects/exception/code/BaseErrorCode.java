package com.example.final_projects.exception.code;

import com.example.final_projects.exception.ErrorReason;

public interface BaseErrorCode {
    ErrorReason getErrorReason();
    default boolean isDefault() { return false; }
}
