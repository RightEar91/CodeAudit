package com.codeaudit.dto;

public record CommitInfo(
        String hash,
        String shortHash,
        String message
) {}
