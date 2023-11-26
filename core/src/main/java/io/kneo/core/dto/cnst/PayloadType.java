package io.kneo.core.dto.cnst;

import lombok.Getter;

@Getter
public enum PayloadType {
    CONTEXT_ACTIONS("actions"), EXCEPTION("exception"), TEXT("text"), VIEW_DATA("view_data"), FORM_DATA("form_data");

    private final String alias;

    PayloadType(String alias) {
        this.alias = alias;
    }

}
