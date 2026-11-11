package com.sentinel.transaction.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BulkUploadResponse {
    private int totalProcessed;
    private int totalFailed;

    /** Rows put through the fraud pipeline. Zero when the caller passed analyze=false. */
    private int totalAnalyzed;

    private List<String> errors;
}
