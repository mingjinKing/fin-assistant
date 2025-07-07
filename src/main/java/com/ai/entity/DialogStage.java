package com.ai.entity;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "dialog_stage")
public class DialogStage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Integer stageId;

    @Column(name = "stage_name", nullable = false, length = 20)
    private String stageName;

    @Column(name = "transition_condition", length = 200)
    private String transitionCondition;

}
