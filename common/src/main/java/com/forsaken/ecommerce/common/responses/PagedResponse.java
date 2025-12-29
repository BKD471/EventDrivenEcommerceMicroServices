package com.forsaken.ecommerce.common.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;
import java.util.Map;


@Builder
public record PagedResponse<T>(

        /** Page content */
        @JsonProperty("content")
        List<T> content,

        /** Current page number (1-based, SQL-style pagination) */
        @JsonProperty("page")
        int page,

        /** Page size / limit */
        @JsonProperty("size")
        int size,

        /** Total number of elements (SQL only) */
        @JsonProperty("totalElements")
        long totalElements,

        /** Total number of pages (SQL only) */
        @JsonProperty("totalPages")
        int totalPages,

        /** Cursor for DynamoDB / keyset pagination */
        Map<String, ?> nextCursor,

        /** Indicates whether this is the last page (optional) */
        @JsonProperty("isLastPage")
        boolean isLastPage
) {

}
