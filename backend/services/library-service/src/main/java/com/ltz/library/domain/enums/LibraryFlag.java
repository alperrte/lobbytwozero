package com.ltz.library.domain.enums;

/** SQL kolonları yalnız bu sabit allowlist'ten seçilir; istemci girdisi kullanılmaz. */
public enum LibraryFlag {
    FAVORITE("favorite"),
    COMPLETED("completed");

    private final String column;

    LibraryFlag(String column) { this.column = column; }

    public String column() { return column; }
}
