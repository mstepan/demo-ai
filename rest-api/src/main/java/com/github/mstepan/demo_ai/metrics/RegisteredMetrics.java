package com.github.mstepan.demo_ai.metrics;

public final class RegisteredMetrics {

    // tags = {"status": "success"}, {"status": "failed"}
    public static final String API_OCI_CHAT_TOTAL = "app_oci_chat_total";

    public static final String API_OCI_CHAT_LATENCY_SECONDS = "app_oci_chat_latency_seconds";

    public static final String APP_LLM_RETRIES_TOTAL = "app_llm_retries_total";

    public static final String APP_EVALUATOR_RELEVANCE_TOTAL = "app_evaluator_relevancy_total";
}
