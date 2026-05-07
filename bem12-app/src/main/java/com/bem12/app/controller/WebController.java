package com.bem12.app.controller;

import com.bem12.app.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {

    private final UserService userService;

    public WebController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           RedirectAttributes redirect) {
        if (password.length() < 6) {
            redirect.addFlashAttribute("error", "Password must be at least 6 characters.");
            return "redirect:/register";
        }
        boolean created = userService.register(email, password);
        if (!created) {
            redirect.addFlashAttribute("error", "An account with that email already exists.");
            return "redirect:/register";
        }
        redirect.addFlashAttribute("success", "Account created! Please log in.");
        return "redirect:/login";
    }

    @GetMapping("/welcome")
    public String welcome(@AuthenticationPrincipal UserDetails user, Model model) {
        model.addAttribute("email", user.getUsername());
        return "welcome";
    }
}
