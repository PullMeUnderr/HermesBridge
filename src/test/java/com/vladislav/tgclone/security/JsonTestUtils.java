package com.vladislav.tgclone.security;

import com.jayway.jsonpath.JsonPath;

final class JsonTestUtils {

    private JsonTestUtils() {
    }

    static String readJson(String payload, String path) {
        return JsonPath.read(payload, path);
    }
}
