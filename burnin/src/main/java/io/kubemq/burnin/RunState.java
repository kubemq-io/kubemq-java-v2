package io.kubemq.burnin;

public enum RunState {
    IDLE("idle"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping"),
    STOPPED("stopped"),
    ERROR("error");

    private final String value;

    RunState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean canStart() {
        return this == IDLE || this == STOPPED || this == ERROR;
    }

    public boolean canStop() {
        return this == STARTING || this == RUNNING;
    }

    public boolean canCleanup() {
        return this == IDLE || this == STOPPED || this == ERROR;
    }

    public boolean isReady() {
        return this == IDLE || this == RUNNING || this == STOPPED || this == ERROR;
    }
}
