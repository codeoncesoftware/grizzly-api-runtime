package fr.codeonce.grizzly.runtime.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

@Configuration
@Primary
public class OpenApiConfig {

    @Autowired
    private BuildProperties buildProperties;

    @Bean
    public OpenAPI customOpenAPI() {
        String buildTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.UK)
                .withZone(ZoneOffset.UTC)
                .format(buildProperties.getTime());

        Info apiInfo = new Info()
                .title("Grizzly Runtime Api")
                .version(buildProperties.getVersion())
                .description("Build Time: %s".formatted(buildTime));

        Parameter authHeader = new Parameter()
                .name("Authorization")
                .description("Bearer {generated_token}")
                .in("header")
                .required(false)
                .schema(new StringSchema());

        return new OpenAPI()
                .info(apiInfo)
                .components(new Components().addParameters("Authorization", authHeader))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}