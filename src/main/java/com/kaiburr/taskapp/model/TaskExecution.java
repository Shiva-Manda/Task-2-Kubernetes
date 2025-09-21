package com.kaiburr.taskapp.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskExecution {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String output;

    public TaskExecution(LocalDateTime startTime, LocalDateTime endTime, String output) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.output = output;
    }
}