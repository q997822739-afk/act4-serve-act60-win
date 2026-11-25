package com.dylan.store;

public class DuplicateNameException extends StoreException {

    private final String name;

    public DuplicateNameException(String name) {
        super("角色名已被占用：" + name);
        this.name = name;
    }

    public String name() {
        return name;
    }
}
