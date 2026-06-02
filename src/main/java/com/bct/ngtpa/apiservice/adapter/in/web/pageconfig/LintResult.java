package com.bct.ngtpa.apiservice.adapter.in.web.pageconfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LintResult {

    private final List<String> errors = new ArrayList<>();

    public void addError(String msg) {
        errors.add(msg);
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
