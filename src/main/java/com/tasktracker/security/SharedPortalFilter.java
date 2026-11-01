package com.tasktracker.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
/** Personal portal adapter. Passwords stay in Authelia; preserve the existing local owner/data. */
final class SharedPortalFilter extends OncePerRequestFilter {
 private final String key; private final Supplier<UserDetails> owner;
 SharedPortalFilter(String key,Supplier<UserDetails> owner){this.key=key;this.owner=owner;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws IOException,ServletException {
  String user=request.getHeader("X-Shared-User"),provided=request.getHeader("X-Shared-Key");
  if(user!=null||provided!=null){
   if(key.length()<40||provided==null||!"tyrak".equals(user)||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),provided.getBytes(StandardCharsets.UTF_8))){response.setStatus(401);return;}
   try {
    UserDetails principal=owner.get();
    if(!principal.isEnabled()||!principal.isAccountNonLocked()||!principal.isAccountNonExpired()){response.setStatus(403);return;}
    var context=SecurityContextHolder.createEmptyContext();
    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,"",principal.getAuthorities()));
    SecurityContextHolder.setContext(context);request.setAttribute("shared.portal.username",user);
   }catch(org.springframework.security.core.AuthenticationException error){response.setStatus(403);return;}
  }
  chain.doFilter(request,response);
 }
}
