package com.ltz.user.application.service;

/** Rızaların tamamı (PUT) ve istemcinin son gördüğü sürüm. */
public record UpdateConsentsCommand(long expectedVersion, boolean recommendation) {
}
