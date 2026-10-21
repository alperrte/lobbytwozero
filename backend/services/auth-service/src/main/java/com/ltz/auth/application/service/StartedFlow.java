package com.ltz.auth.application.service;

import java.net.URI;

/** Başlatılan akış: tarayıcının yönlendirileceği adres ve cookie'ye yazılacak state. */
public record StartedFlow(URI redirectUri, String state) {

    @Override
    public String toString() {
        return "StartedFlow[redirectUri=***, state=***]";
    }
}
