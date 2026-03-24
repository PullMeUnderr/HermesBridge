package com.vladislav.tgclone.account;

public enum AccountIdentityProvider {
    PASSWORD("password"),
    TELEGRAM("telegram"),
    TDLIGHT("tdlight");

    private final String value;

    AccountIdentityProvider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
