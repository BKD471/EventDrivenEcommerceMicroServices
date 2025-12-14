package com.forsaken.ecommerce.notification.configs.dynamodb;

import lombok.Builder;

@Builder
public record AwsCredentialsProperties(
        String accessKeyId,
        String secretKey,
        String region
) {

}
