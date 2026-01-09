package com.github.mstepan.demo_ai.domain;

import jakarta.validation.constraints.NotBlank;

public record Question(@NotBlank(message = "'question' is required field") String question) {}
