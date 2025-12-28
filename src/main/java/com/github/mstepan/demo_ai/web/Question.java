package com.github.mstepan.demo_ai.web;

import jakarta.validation.constraints.NotBlank;

public record Question(@NotBlank(message = "'question' is required field") String question) {}
