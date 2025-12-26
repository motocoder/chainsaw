package org.apache.log4j.net.payload;

public class LogEvent {

    public String message;
    public String name;
    public String threadName;
    public String level;
    public long time;

    public LogEvent() {
    }

    public LogEvent(
        final String name,
        final String message,
        final String level,
        final String threadName,
        final long time
    ) {
        this.message = message;
        this.name = name;
        this.level = level;
        this.threadName = threadName;
        this.time = time;
    }
}
