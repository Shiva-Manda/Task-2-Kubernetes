package com.kaiburr.taskapp.model;

import java.time.LocalDateTime;
import lombok.Data;

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