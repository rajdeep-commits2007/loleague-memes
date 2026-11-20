package com.loleague.auth.college;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * The college list lives in {@code colleges.json}. It's loaded once at startup and is the single source
 * of truth for the sign-up dropdown and for validating the chosen college.
 */
@Component
public class CollegeCatalog {

    public record College(String id, String name, String city) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CollegeFile(List<College> colleges) {
    }

    private final List<College> colleges;
    private final Map<String, College> byId;

    public CollegeCatalog(JsonMapper jsonMapper,
                          @Value("${app.colleges-file:classpath:colleges.json}") Resource file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            CollegeFile parsed = jsonMapper.readValue(in, CollegeFile.class);
            List<College> sorted = new ArrayList<>(parsed.colleges());
            sorted.sort(Comparator.comparing(College::name, String.CASE_INSENSITIVE_ORDER));
            this.colleges = List.copyOf(sorted);
        }
        Map<String, College> map = new HashMap<>();
        colleges.forEach(c -> map.put(c.id(), c));
        this.byId = Map.copyOf(map);
    }

    public List<College> all() {
        return colleges;
    }

    public Optional<College> find(String id) {
        return Optional.ofNullable(id).map(byId::get);
    }
}
