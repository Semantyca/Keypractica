package com.semantyca.server.cnst;

/**
 * @author Kayra created 25-11-2016
 */
public enum VisibilityMode {
    UNKNOWN(0), NORMAL(102), HIDDEN(103);

    private int code;

    VisibilityMode(int code) {
        this.code = code;
    }

    public static VisibilityMode getType(int code) {
        for (VisibilityMode type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public int getCode() {
        return code;
    }
}
