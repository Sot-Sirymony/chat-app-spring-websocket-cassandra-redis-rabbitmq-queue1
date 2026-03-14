package br.com.jorgeacetozi.ebookChat.configuration;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

import br.com.jorgeacetozi.ebookChat.authentication.domain.service.JwtTokenService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({ JwtProperties.class, MinioProperties.class, AbacProperties.class, FileEncryptionProperties.class, br.com.jorgeacetozi.ebookChat.dlp.configuration.DlpProperties.class, br.com.jorgeacetozi.ebookChat.dlp.configuration.PresidioProperties.class })
public class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private FileDownloadCorsFilter fileDownloadCorsFilter;

	@Override
	public void configure(WebSecurity web) throws Exception {
		web.ignoring().antMatchers("/ws/**");
	}
	
	@Override
	protected void configure(HttpSecurity http) throws Exception {
        http
	        .csrf().disable()
	        .cors()
	        .and()
	        .addFilterBefore(fileDownloadCorsFilter, WebAsyncManagerIntegrationFilter.class)
	        .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class)
	        .formLogin()
	        	.loginProcessingUrl("/login")
	        	.loginPage("/")
	        	.defaultSuccessUrl("/chat")
	        	.and()
	        .logout()
	        	.logoutSuccessUrl("/")
	        	.and()
	        .headers().frameOptions().disable()
	        .and()
	        .exceptionHandling()
	        .accessDeniedHandler(fileDownloadAccessDeniedHandler())
	        .and()
	        .authorizeRequests()
	        	.antMatchers("/login", "/new-account", "/", "/api/auth/token", "/api/auth/register").permitAll()
	        	.antMatchers("/ws/**").permitAll()
	        	.antMatchers(HttpMethod.OPTIONS, "/api/files/*/download").permitAll()
	        	.antMatchers("/approvals").authenticated()
	        	.antMatchers("/analytics").hasRole("ADMIN")
	        	.antMatchers(HttpMethod.POST, "/chatroom").hasRole("ADMIN")
	        	.antMatchers("/metrics", "/metrics/**").hasRole("ADMIN")
	        	.anyRequest().authenticated();
	}

	@Bean
	@Override
	public AuthenticationManager authenticationManagerBean() throws Exception {
		return super.authenticationManagerBean();
	}
	
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(bCryptPasswordEncoder());
    }
    
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** When Spring returns 403 for /api/files/* (e.g. missing ROLE_USER), set X-File-Deny-Reason so frontend can suggest re-login. */
    @Bean
    public AccessDeniedHandler fileDownloadAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> {
            String uri = request.getRequestURI();
            if (uri != null && uri.contains("/api/files/") && uri.endsWith("/download")) {
                response.setHeader("X-File-Deny-Reason", "FORBIDDEN_ROLE");
            }
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Not allowed to download this file");
        };
    }
}
