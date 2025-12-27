package com.forsaken.ecommerce.common.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;


@Builder
public record PagedResponse<T>(

        @JsonProperty("content")
        List<T> content,

        @JsonProperty("page")
        int page,

        @JsonProperty("size")
        int size,

        @JsonProperty("totalElements")
        long totalElements,

        @JsonProperty("totalPages")
        int totalPages,

        @JsonProperty("isLastPage")
        boolean isLastPage
) {

}
