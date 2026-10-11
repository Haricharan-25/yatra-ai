package com.yatraai.controller;

import com.yatraai.model.StoryRequest;
import com.yatraai.model.StoryResponse;
import com.yatraai.service.StoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/story")
public class StoryController {

    private static final Logger logger = LoggerFactory.getLogger(StoryController.class);

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping("/generate")
    public ResponseEntity<StoryResponse> generateStory(@Valid @RequestBody StoryRequest request) {
        logger.info("Received story generation request for city: {}", request.city());
        StoryResponse response = storyService.generateStory(request);
        return ResponseEntity.ok(response);
    }
}
