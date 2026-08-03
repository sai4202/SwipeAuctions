package com.swipeauctions.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Serves uploaded listing images (stored on disk under app.upload.dir) at /uploads/**. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    // SockJS's JSONP/iframe fallback transports (used by browsers that need cookie-based sticky
    // sessions for /ws) write their /info response with Content-Type application/javascript.
    // Jackson's converter only declares support for application/json by default, so without this
    // it throws HttpMessageNotWritableException ("No converter for LinkedHashMap with preset
    // Content-Type 'application/javascript'"), which Spring Security's error dispatch then turns
    // into a 503 for any client that sends cookies (SockJS reports cookie_needed=true).
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                List<MediaType> supportedMediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supportedMediaTypes.add(MediaType.valueOf("application/javascript"));
                jacksonConverter.setSupportedMediaTypes(supportedMediaTypes);
            }
        }
    }
}
