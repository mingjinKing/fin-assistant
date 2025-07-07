package com.ai.controller;

import com.ai.schedule.UpdateKnowledgeSchedule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KnowledgeController {

    @Autowired
    private UpdateKnowledgeSchedule updateKnowledgeSchedule;

    @GetMapping("/initKnowledgeBase")
    public String initKnowledgeBase() throws Exception {
        updateKnowledgeSchedule.updateKnowledgeBase();
        return "success";
    }
}
