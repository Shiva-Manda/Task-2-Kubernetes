package com.kaiburr.taskapp.controller;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;

import com.kaiburr.taskapp.model.Task;
import com.kaiburr.taskapp.model.TaskExecution;
import com.kaiburr.taskapp.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Read All → GET /tasks
    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    // Read by ID → GET /tasks/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/findByName")
    public ResponseEntity<List<Task>> findTasksByName(@RequestParam String name) {
        List<Task> tasks = taskRepository.findByNameContaining(name);
        return tasks.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(tasks);
    }

    // Create → POST /tasks
    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        String command = task.getCommand();
        if (command == null || command.isBlank() || command.contains("rm") || command.contains("sudo")) {
            return ResponseEntity.badRequest().build();
        }
        Task savedTask = taskRepository.save(task);
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }

    // Update → PUT /tasks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable String id, @RequestBody Task updatedTask) {
        return taskRepository.findById(id)
                .map(existingTask -> {
                    existingTask.setName(updatedTask.getName());
                    existingTask.setOwner(updatedTask.getOwner());
                    existingTask.setCommand(updatedTask.getCommand());
                    return ResponseEntity.ok(taskRepository.save(existingTask));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Delete → DELETE /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Execute → POST /tasks/{id}/execute
    @PostMapping("/{id}/execute")
    public ResponseEntity<Task> executeTask(@PathVariable String id) {
        Optional<Task> optionalTask = taskRepository.findById(id);
        if (optionalTask.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Task task = optionalTask.get();
        LocalDateTime startTime = LocalDateTime.now();
        String output = "";
        String podName = "task-runner-" + task.getId() + "-" + System.currentTimeMillis();

        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            Pod pod = new PodBuilder()
                    .withNewMetadata().withName(podName).endMetadata()
                    .withNewSpec()
                        .addNewContainer()
                            .withName("task-container")
                            .withImage("busybox")
                            .withCommand("sh", "-c", task.getCommand())
                        .endContainer()
                        .withRestartPolicy("Never")
                    .endSpec()
                    .build();

            client.pods().inNamespace("default").create(pod);
            client.pods().inNamespace("default").withName(podName)
                    .waitUntilCondition(p ->
                        "Succeeded".equals(p.getStatus().getPhase()) ||
                        "Failed".equals(p.getStatus().getPhase()),
                        5, TimeUnit.MINUTES);
            output = client.pods().inNamespace("default").withName(podName).getLog();
        } catch (Exception e) {
            output = "Kubernetes pod execution failed: " + e.getMessage();
            e.printStackTrace();
        } finally {
            try (KubernetesClient client = new KubernetesClientBuilder().build()) {
                client.pods().inNamespace("default").withName(podName).delete();
            } catch (Exception ignored) {}
            LocalDateTime endTime = LocalDateTime.now();
            TaskExecution execution = new TaskExecution(startTime, endTime, output);
            task.getTaskExecutions().add(execution);
            taskRepository.save(task);
        }
        return ResponseEntity.ok(task);
    }
}