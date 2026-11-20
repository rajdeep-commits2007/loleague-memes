package com.loleague.meme.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    private AwsCredentialsProvider credentials(MemeProperties.Aws aws) {
        if (aws.accessKeyId() != null && !aws.accessKeyId().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(aws.accessKeyId(), aws.secretAccessKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(MemeProperties props) {
        MemeProperties.Aws aws = props.aws();
        var builder = S3Client.builder()
                .region(Region.of(aws.region()))
                .credentialsProvider(credentials(aws));
        if (aws.endpoint() != null && !aws.endpoint().isBlank()) {
            // e.g. MinIO / LocalStack during local development
            builder.endpointOverride(URI.create(aws.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(MemeProperties props) {
        MemeProperties.Aws aws = props.aws();
        var builder = S3Presigner.builder()
                .region(Region.of(aws.region()))
                .credentialsProvider(credentials(aws));
        if (aws.endpoint() != null && !aws.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(aws.endpoint()))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
