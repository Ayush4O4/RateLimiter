package com.ayush.RateLimiter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
//@RequestMapping("")
public class Controller {

    @GetMapping("/limited")
    public String limitedRate(){
        return "Got Through";
    }

    @GetMapping("/unlimited")
    public String unlimitedRate(){
       return "no limiting logic yet!";
    }

}
