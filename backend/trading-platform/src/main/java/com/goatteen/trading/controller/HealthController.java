package com.goatteen.trading.controller;


import io.swagger.v3.oas.annotations.Operation;

import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
public class HealthController {


    @Operation(
        summary = "Backend health check",
        description = "Checks whether the LEAP backend service is running"
    )
    @GetMapping("/health")
    public String health(){

        return "LEAP Backend is running!";

    }

}