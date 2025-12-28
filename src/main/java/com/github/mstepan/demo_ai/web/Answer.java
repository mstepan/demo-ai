package com.github.mstepan.demo_ai.web;

import jakarta.validation.constraints.NotBlank;

public record Answer(@NotBlank(message = "'answer' is required field") String answer) {}
