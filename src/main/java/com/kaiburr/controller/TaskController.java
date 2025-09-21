package com.kaiburr.taskapp.controller;

import com.kaiburr.taskapp.model.Task;
import com.kaiburr.taskapp.model.TaskExecution;
import com.kaiburr.taskapp.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        String command = task.getCommand();
        if (command == null || command.isBlank() || command.contains("rm") || command.contains("sudo")) {
            return ResponseEntity.badRequest().build();
        }
        Task savedTask = taskRepository.save(task);
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/findByName")
    public ResponseEntity<List<Task>> findTasksByName(@RequestParam String name) {
        List<Task> tasks = taskRepository.findByNameContaining(name);
        if (tasks.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tasks);
    }
    
    @PutMapping("/{id}/execute")
    public ResponseEntity<Task> executeTask(@PathVariable String id) {
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Task task = optionalTask.get();
        LocalDateTime startTime = LocalDateTime.now();
        String output = "";

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("sh", "-c", task.getCommand());
            Process process = builder.start();
            
            StringBuilder result = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
            }

            process.waitFor(5, TimeUnit.MINUTES);
            output = result.toString();

        } catch (Exception e) {
            output = "Execution failed: " + e.getMessage();
        } finally {
            LocalDateTime endTime = LocalDateTime.now();
            TaskExecution execution = new TaskExecution(startTime, endTime, output);
            task.getTaskExecutions().add(execution);
            taskRepository.save(task);
        }

        return ResponseEntity.ok(task);
    }
}