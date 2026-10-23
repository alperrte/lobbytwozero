package com.ltz.hardware.domain.enums;

public enum HardwareVisibility {
    PRIVATE, FRIENDS, PUBLIC;

    public boolean permits(boolean friends) {
        return this == PUBLIC || (this == FRIENDS && friends);
    }
}
