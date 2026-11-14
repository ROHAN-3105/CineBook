package com.user_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.user_service.entity.Client;
import com.user_service.entity.UserSignInDTO;
import com.user_service.service.UserService;
import com.user_service.utils.JwtUtil;

@RestController
@RequestMapping("/api/user")
public class UserController {
	
	@Autowired
	private UserService userService;

	@Autowired
	private JwtUtil jwtUtil;

	@Autowired
	private AuthenticationManager authenticationManager;
	
	@GetMapping("/test")
	public String test() {
		return "Private Access";
	}

	@GetMapping("/me")
	public ResponseEntity<?> me(Authentication authentication) {
		Client client = userService.findByUsername(authentication.getName());
		if (client == null) return ResponseEntity.notFound().build();
		return ResponseEntity.ok(new CurrentUser(client.getId(), client.getUsername(), client.getRole()));
	}

	public record CurrentUser(Long id, String username, String role) {}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody Client user) {
		Client registered = userService.registerUser(user.getUsername(), user.getPassword(), user.getRole());
		return ResponseEntity.ok(new CurrentUser(registered.getId(), registered.getUsername(), registered.getRole()));
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody UserSignInDTO user) {
		try {
			authenticationManager
					.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
			
			Client clientInfo = userService.findByUsername(user.getUsername());
			
			String token = jwtUtil.generateToken(user.getUsername(), clientInfo.getRole());
			
			return ResponseEntity.ok(token);
			
		} catch (AuthenticationException e) {
			return ResponseEntity.status(401).body("Invalid Credentials");
		}
	}
}
