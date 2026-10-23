package com.tasktracker.security;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("kova")
public class PilotFrontend {
    @GetMapping({"/","/dashboard","/projects","/tasks","/incidents","/overtime","/team","/reminders","/acceso"})
    String index() {return "forward:/index.html";}
}
