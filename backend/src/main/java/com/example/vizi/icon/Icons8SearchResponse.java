package com.example.vizi.icon;

import java.util.List;

record Icons8SearchResponse(
        boolean configured,
        boolean creditRequired,
        String creditText,
        String creditUrl,
        String message,
        List<Icons8Icon> icons
) {
    static Icons8SearchResponse disabled(String message) {
        return new Icons8SearchResponse(
                false,
                true,
                "Icons by Icons8",
                "https://icons8.com",
                message,
                List.of()
        );
    }

    static Icons8SearchResponse enabled(String message, List<Icons8Icon> icons) {
        return new Icons8SearchResponse(
                true,
                true,
                "Icons by Icons8",
                "https://icons8.com",
                message,
                icons
        );
    }
}
